package com.resumescreener.api.upload;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.common.entity.ScreeningSession;
import com.resumescreener.common.entity.Candidate;
import com.resumescreener.common.enums.CandidateStatus;
import com.resumescreener.common.repository.CandidateRepository;
import com.resumescreener.common.repository.ScreeningSessionRepository;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MinioUploadService {

    private final MinioClient minioClient;
    private final ScreeningSessionRepository sessionRepository;
    private final CandidateRepository candidateRepository;

    @Value("${minio.bucket}")
    private String bucket;

    public void checkOwnership(String sessionId, String userEmail) {
        ScreeningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found",
                        "No session exists for id " + sessionId));
        if (!session.getUser().getEmail().equals(userEmail)) {
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

    @Transactional
    public List<String> uploadResumes(String sessionId, MultipartFile[] files, String userEmail) {
        checkOwnership(sessionId, userEmail);
        ScreeningSession session = sessionRepository.findById(sessionId).get();

        candidateRepository.deleteBySessionId(sessionId);

        List<String> candidateIds = new ArrayList<>();
        for (MultipartFile file : files) {
            if (candidateIds.size() >= 200) break;

            String key = "resumes/" + sessionId + "/" + UUID.randomUUID() + ".pdf";

            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType("application/pdf")
                        .build());

                String originalName = file.getOriginalFilename();
                String candidateName = (originalName != null)
                        ? originalName.replaceAll("(?i)\\.pdf$", "").replace("_", " ").replace("-", " ")
                        : "Unknown";

                Candidate candidate = Candidate.builder()
                        .session(session)
                        .resumeFileKey(key)
                        .name(candidateName)
                        .status(CandidateStatus.PENDING)
                        .build();
                candidateRepository.save(candidate);
                candidateIds.add(candidate.getId());
            } catch (Exception e) {
                throw storageFailure("Failed to upload resume " + file.getOriginalFilename(), e);
            }
        }
        return candidateIds;
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
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
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