package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmClient;
import com.resumescreener.batch.llm.LlmException;
import com.resumescreener.batch.pdf.PdfTextExtractor;
import com.resumescreener.batch.storage.MinioStorageService;
import com.resumescreener.common.llm.LlmScoreDto;
import com.resumescreener.common.session.BatchJob;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.ScoringWeights;
import com.resumescreener.common.session.CandidateStatus;
import com.resumescreener.common.session.ReviewStatus;
import com.resumescreener.common.session.BatchJobRepository;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateScoreRepository;
import com.resumescreener.common.session.CandidateTextRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandidateProcessingServiceTest {

    @Test
    void processSingleCandidateDoesNotPersistScoreWhenLlmScoringFails() throws Exception {
        SaveTracker candidateSaves = new SaveTracker();
        SaveTracker scoreSaves = new SaveTracker();
        SaveTracker batchJobSaves = new SaveTracker();
        FakeMinioStorageService minioStorageService = new FakeMinioStorageService();
        FakePdfTextExtractor pdfTextExtractor = new FakePdfTextExtractor();
        ThrowingLlmClient llmClient = new ThrowingLlmClient();
        CandidateProcessingService service = new CandidateProcessingService(
                repositoryProxy(CandidateRepository.class, candidateSaves),
                repositoryProxy(CandidateScoreRepository.class, scoreSaves),
                repositoryProxy(BatchJobRepository.class, batchJobSaves),
                repositoryProxy(CandidateTextRepository.class, new SaveTracker()),
                minioStorageService,
                pdfTextExtractor,
                llmClient
        );
        Candidate candidate = Candidate.builder()
                .id("candidate-1")
                .email("candidate@example.com")
                .resumeFileKey("resumes/candidate.pdf")
                .status(CandidateStatus.PENDING)
                .build();
        ScoringWeights weights = ScoringWeights.builder()
                .technicalSkills(30.0)
                .experience(25.0)
                .education(15.0)
                .softSkills(20.0)
                .cultureFit(10.0)
                .build();
        BatchJob job = BatchJob.builder()
                .id("job-1")
                .processed(0)
                .failed(0)
                .build();

        assertThrows(LlmException.class,
                () -> service.processSingleCandidate(candidate, "job description", weights, job));

        assertEquals("resumes/candidate.pdf", minioStorageService.requestedKey);
        assertEquals("resume text", llmClient.receivedResumeText);
        assertEquals("job description", llmClient.receivedJdText);
        assertEquals(0, scoreSaves.writeCount);
        assertEquals(0, candidateSaves.writeCount);
        assertEquals(0, batchJobSaves.writeCount);
    }

    @Test
    void failureReasonIsStoredAndTruncatedTo500() {
        Candidate candidate = Candidate.builder().id("c1").status(CandidateStatus.PENDING).build();
        CandidateProcessingService service = new CandidateProcessingService(
                repositoryProxy(CandidateRepository.class, new SaveTracker()),
                repositoryProxy(CandidateScoreRepository.class, new SaveTracker()),
                repositoryProxy(BatchJobRepository.class, new SaveTracker()),
                repositoryProxy(CandidateTextRepository.class, new SaveTracker()),
                null, null, null);

        service.handleCandidateFailure(candidate, BatchJob.builder().id("j1").processed(0).failed(0).build(),
                "x".repeat(600));

        assertEquals(CandidateStatus.FAILED, candidate.getStatus());
        assertEquals(500, candidate.getFailureReason().length());
    }

    @Test
    void rescoreClearsFailureReasonAndKeepsReviewStatus() throws Exception {
        Candidate candidate = Candidate.builder().id("c1").resumeFileKey("resumes/c1.pdf")
                .status(CandidateStatus.FAILED).failureReason("Could not read text")
                .reviewStatus(ReviewStatus.SHORTLISTED).build();
        CandidateProcessingService service = new CandidateProcessingService(
                repositoryProxy(CandidateRepository.class, new SaveTracker()),
                repositoryProxy(CandidateScoreRepository.class, new SaveTracker()),
                repositoryProxy(BatchJobRepository.class, new SaveTracker()),
                repositoryProxy(CandidateTextRepository.class, new SaveTracker()),
                new FakeMinioStorageService(), new FakePdfTextExtractor(), new ScoringLlmClient());

        service.processSingleCandidate(candidate, "jd", ScoringWeights.builder().technicalSkills(30.0)
                .experience(25.0).education(15.0).softSkills(20.0).cultureFit(10.0).build(),
                BatchJob.builder().id("j1").processed(0).failed(0).build());

        assertEquals(CandidateStatus.SCORED, candidate.getStatus());
        assertEquals(null, candidate.getFailureReason());
        assertEquals(ReviewStatus.SHORTLISTED, candidate.getReviewStatus());
    }

    @SuppressWarnings("unchecked")
    private static <T> T repositoryProxy(Class<T> repositoryType, SaveTracker saveTracker) {
        InvocationHandler handler = new RepositoryInvocationHandler(saveTracker);
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                handler
        );
    }

    private static class RepositoryInvocationHandler implements InvocationHandler {
        private final SaveTracker saveTracker;

        private RepositoryInvocationHandler(SaveTracker saveTracker) {
            this.saveTracker = saveTracker;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            if ("save".equals(method.getName()) || "saveAll".equals(method.getName())) {
                saveTracker.writeCount++;
                return args == null || args.length == 0 ? null : args[0];
            }
            if ("findById".equals(method.getName())) {
                return Optional.empty();
            }
            return defaultValue(method.getReturnType());
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "RepositoryProxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == Void.TYPE) {
                return null;
            }
            if (returnType == Boolean.TYPE) {
                return false;
            }
            if (returnType == Integer.TYPE || returnType == Long.TYPE || returnType == Short.TYPE || returnType == Byte.TYPE) {
                return 0;
            }
            if (returnType == Double.TYPE || returnType == Float.TYPE) {
                return 0.0;
            }
            return null;
        }
    }

    private static class SaveTracker {
        private int writeCount;
    }

    private static class FakeMinioStorageService extends MinioStorageService {
        private String requestedKey;

        private FakeMinioStorageService() {
            super(null);
        }

        @Override
        public byte[] downloadBytes(String key) {
            requestedKey = key;
            return new byte[]{1, 2, 3};
        }
    }

    private static class FakePdfTextExtractor extends PdfTextExtractor {
        @Override
        public String extract(byte[] pdfBytes) {
            return "resume text";
        }
    }

    private static class ThrowingLlmClient extends LlmClient {
        private String receivedJdText;
        private String receivedResumeText;

        private ThrowingLlmClient() {
            super(null, null);
        }

        @Override
        public LlmScoreDto score(String jdText, String resumeText) {
            receivedJdText = jdText;
            receivedResumeText = resumeText;
            throw new LlmException("Invalid LLM score");
        }
    }

    private static class ScoringLlmClient extends LlmClient {
        private ScoringLlmClient() {
            super(null, null);
        }

        @Override
        public LlmScoreDto score(String jdText, String resumeText) {
            LlmScoreDto dto = new LlmScoreDto();
            dto.setTechnicalSkills(80);
            dto.setExperience(70);
            dto.setEducation(60);
            dto.setSoftSkills(50);
            dto.setCultureFit(40);
            return dto;
        }
    }
}
