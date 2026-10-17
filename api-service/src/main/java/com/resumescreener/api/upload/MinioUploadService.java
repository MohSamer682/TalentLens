package com.resumescreener.api.upload;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.ScreeningSessionRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.resumescreener.api.upload.ResumeIntake.IncomingResume;
import com.resumescreener.api.upload.ResumeIntake.IntakeResult;
import com.resumescreener.api.upload.ResumeIntake.RejectedFile;
import com.resumescreener.api.upload.dto.UploadResumesResponse;
import com.resumescreener.common.session.SessionStatus;
import org.springframework.dao.DataIntegrityViolationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MinioUploadService {

    public static final int MAX_RESUMES_PER_SESSION = 1000;

    private final MinioClient minioClient;
    private final MinioClient presignClient;
    private final ScreeningSessionRepository sessionRepository;
    private final CandidateRepository candidateRepository;

    @Value("${minio.bucket}")
    private String bucket;

    public void checkOwnership(String sessionId, String userEmail) {
        ScreeningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found",
                        "No session exists for id " + sessionId));
        if (!session.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized");
        }
    }

    @Transactional
    public String uploadJd(String sessionId, MultipartFile file, String userEmail) {
        checkOwnership(sessionId, userEmail);
        ScreeningSession session = sessionRepository.findById(sessionId).get();

        String key = "jd/" + sessionId + "/" + UUID.randomUUID() + ".pdf";

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType("application/pdf")
                    .build());

            session.setJdFileKey(key);
            sessionRepository.save(session);
            return key;
        } catch (Exception e) {
            throw storageFailure("Failed to upload JD", e);
        }
    }

    /**
     * Adds resumes to the session; existing candidates are never touched. Each candidate is saved on its own,
     * so files stored before a storage failure stay in the session (a retry skips them as duplicates).
     */
    public UploadResumesResponse uploadResumes(String sessionId, MultipartFile[] files, String userEmail) {
        checkOwnership(sessionId, userEmail);
        ScreeningSession session = sessionRepository.findById(sessionId).get();
        if (session.getStatus() == SessionStatus.RUNNING) {
            throw ApiException.validation("Wait for scoring to finish before adding resumes",
                    "Session " + sessionId + " is in status RUNNING");
        }

        IntakeResult intake = ResumeIntake.expand(files);
        List<RejectedFile> rejected = new ArrayList<>(intake.rejected());
        List<String> candidateIds = new ArrayList<>();
        Set<String> seenInRequest = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        long room = MAX_RESUMES_PER_SESSION - candidateRepository.countBySessionId(sessionId);

        for (IncomingResume resume : intake.accepted()) {
            if (!seenInRequest.add(resume.sha256())
                    || candidateRepository.existsBySessionIdAndContentHash(sessionId, resume.sha256())) {
                duplicates.add(resume.fileName());
                continue;
            }
            if (candidateIds.size() >= room) {
                rejected.add(new RejectedFile(resume.fileName(),
                        "This session already holds " + MAX_RESUMES_PER_SESSION + " resumes"));
                continue;
            }

            String key = "resumes/" + sessionId + "/" + UUID.randomUUID() + ".pdf";
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(new ByteArrayInputStream(resume.bytes()), resume.bytes().length, -1)
                        .contentType("application/pdf")
                        .build());
            } catch (Exception e) {
                throw storageFailure("Failed to upload resume " + resume.fileName(), e);
            }

            Candidate candidate = Candidate.builder()
                    .session(session)
                    .resumeFileKey(key)
                    .name(candidateNameFrom(resume.fileName()))
                    .originalFileName(resume.fileName())
                    .contentHash(resume.sha256())
                    .status(CandidateStatus.PENDING)
                    .build();
            try {
                candidateRepository.save(candidate);
            } catch (DataIntegrityViolationException e) {
                // A concurrent upload stored the same file first; the unique index caught it
                removeObjectQuietly(key);
                duplicates.add(resume.fileName());
                continue;
            }
            candidateIds.add(candidate.getId());
        }
        return new UploadResumesResponse(sessionId, candidateIds.size(), candidateIds, duplicates, rejected);
    }

    @Transactional
    public void removeResume(String sessionId, String candidateId, String userEmail) {
        checkOwnership(sessionId, userEmail);
        ScreeningSession session = sessionRepository.findById(sessionId).get();
        if (session.getStatus() == SessionStatus.RUNNING) {
            throw ApiException.validation("Wait for scoring to finish before removing resumes",
                    "Session " + sessionId + " is in status RUNNING");
        }
        Candidate candidate = candidateRepository.findById(candidateId)
                .filter(c -> c.getSession() != null && sessionId.equals(c.getSession().getId()))
                .orElseThrow(() -> ApiException.notFound("Resume not found",
                        "Candidate " + candidateId + " does not belong to session " + sessionId));
        candidateRepository.delete(candidate);
        if (candidate.getResumeFileKey() != null) {
            removeObjectQuietly(candidate.getResumeFileKey());
        }
    }

    static String candidateNameFrom(String fileName) {
        return fileName.replaceAll("(?i)\\.pdf$", "").replace("_", " ").replace("-", " ");
    }

    private void removeObjectQuietly(String key) {
        try {
            minioClient.removeObject(io.minio.RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MinioUploadService.class)
                    .warn("Failed to delete MinIO object {}: {}", key, e.getMessage());
        }
    }

    public void deleteSessionFiles(String sessionId) {
        String[] prefixes = {
            "jd/" + sessionId + "/",
            "resumes/" + sessionId + "/",
            "reports/" + sessionId + "/"
        };
        for (String prefix : prefixes) {
            try {
                var results = minioClient.listObjects(
                    io.minio.ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .recursive(true)
                        .build());
                for (var result : results) {
                    String objectName = result.get().objectName();
                    minioClient.removeObject(
                        io.minio.RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MinioUploadService.class)
                    .warn("Failed to delete MinIO object for prefix {}: {}", prefix, e.getMessage());
            }
        }
    }

    public String getPresignedUrl(String key) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(15, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw storageFailure("Failed to generate a download link", e);
        }
    }

    public byte[] downloadBytes(String key) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw storageFailure("Failed to download file from object storage", e);
        }
    }

    /**
     * Object-storage failures reach the client as MINIO_UNAVAILABLE rather than a validation error.
     * The cause is preserved so name-resolution failures stay diagnosable.
     */
    private static ApiException storageFailure(String message, Exception cause) {
        ApiException failure = new ApiException(HttpStatus.BAD_GATEWAY, ApiErrorCode.MINIO_UNAVAILABLE,
                message, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        failure.initCause(cause);
        return failure;
    }
}