package com.resumescreener.api.upload;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.upload.dto.UploadResumesResponse;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.common.user.User;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioUploadServiceTest {

    private final MinioClient minio = mock(MinioClient.class);
    private final ScreeningSessionRepository sessions = mock(ScreeningSessionRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final MinioUploadService service = new MinioUploadService(minio, minio, sessions, candidates);
    private final ScreeningSession session = ScreeningSession.builder()
            .id("s1")
            .user(User.builder().email("Recruiter@Example.com").build())
            .status(SessionStatus.DONE)
            .build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "test");
        when(sessions.findById("s1")).thenReturn(Optional.of(session));
        AtomicInteger ids = new AtomicInteger();
        when(candidates.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0);
            c.setId("c" + ids.incrementAndGet());
            return c;
        });
    }

    @Test
    void appendsAndNeverDeletesExistingCandidates() throws Exception {
        when(candidates.countBySessionId("s1")).thenReturn(5L);

        UploadResumesResponse result = service.uploadResumes("s1",
                new MultipartFile[]{pdf("jane_doe.pdf", "a"), pdf("john-smith.PDF", "b")}, "recruiter@example.com");

        assertEquals(2, result.getUploadedCount());
        verify(candidates, never()).deleteBySessionId(anyString());
        verify(minio, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void skipsDuplicateByHash() {
        when(candidates.existsBySessionIdAndContentHash("s1", ResumeIntake.sha256(pdfBytes("a")))).thenReturn(true);

        UploadResumesResponse result = service.uploadResumes("s1",
                new MultipartFile[]{pdf("old.pdf", "a"), pdf("new.pdf", "b"), pdf("new-copy.pdf", "b")},
                "recruiter@example.com");

        assertEquals(1, result.getUploadedCount());
        assertEquals(2, result.getDuplicateCount());
    }

    @Test
    void refusesUploadWhileRunning() {
        session.setStatus(SessionStatus.RUNNING);

        ApiException e = assertThrows(ApiException.class, () -> service.uploadResumes("s1",
                new MultipartFile[]{pdf("a.pdf", "a")}, "recruiter@example.com"));
        assertEquals("Wait for scoring to finish before adding resumes", e.getMessage());
    }

    @Test
    void rejectsFilesBeyondSessionCap() {
        when(candidates.countBySessionId("s1")).thenReturn((long) MinioUploadService.MAX_RESUMES_PER_SESSION - 1);

        UploadResumesResponse result = service.uploadResumes("s1",
                new MultipartFile[]{pdf("a.pdf", "a"), pdf("b.pdf", "b")}, "recruiter@example.com");

        assertEquals(1, result.getUploadedCount());
        assertEquals("This session already holds 1000 resumes", result.getRejected().get(0).reason());
    }

    @Test
    void reportsInvalidFilesAndKeepsValidOnes() {
        MockMultipartFile notPdf = new MockMultipartFile("files", "notes.txt", "text/plain", "hello".getBytes());

        UploadResumesResponse result = service.uploadResumes("s1",
                new MultipartFile[]{notPdf, pdf("a.pdf", "a")}, "recruiter@example.com");

        assertEquals(1, result.getUploadedCount());
        assertEquals("notes.txt", result.getRejected().get(0).fileName());
        assertEquals("Not a PDF", result.getRejected().get(0).reason());
    }

    @Test
    void ownershipCheckIgnoresEmailCase() {
        service.checkOwnership("s1", "RECRUITER@example.COM");
    }

    @Test
    void removeResumeRefusedWhileRunning() {
        session.setStatus(SessionStatus.RUNNING);

        assertThrows(ApiException.class, () -> service.removeResume("s1", "c1", "recruiter@example.com"));
        verify(candidates, never()).delete(any());
    }

    @Test
    void removeResumeRejectsCandidateFromAnotherSession() {
        ScreeningSession other = ScreeningSession.builder().id("s2").build();
        when(candidates.findById("c9")).thenReturn(Optional.of(Candidate.builder().id("c9").session(other).build()));

        assertThrows(ApiException.class, () -> service.removeResume("s1", "c9", "recruiter@example.com"));
        verify(candidates, never()).delete(any());
    }

    @Test
    void candidateNameDropsExtensionAndSeparators() {
        assertEquals("jane doe", MinioUploadService.candidateNameFrom("jane_doe.PDF"));
    }

    private static MockMultipartFile pdf(String name, String body) {
        return new MockMultipartFile("files", name, "application/pdf", pdfBytes(body));
    }

    private static byte[] pdfBytes(String body) {
        return ("%PDF-1.4 " + body).getBytes(StandardCharsets.US_ASCII);
    }
}
