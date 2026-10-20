package com.resumescreener.api.session;

import com.resumescreener.api.config.GlobalExceptionHandler;
import com.resumescreener.api.session.dto.CreateSessionRequest;
import com.resumescreener.common.session.ScoringWeights;
import com.resumescreener.common.session.ScreeningSession;
import com.resumescreener.common.user.User;
import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.ScreeningSessionRepository;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest {

    private final Principal auth = new TestingAuthenticationToken("recruiter@example.com", null);

    @Test
    void createRejectsBlankTitleWithJsonErrorBody() throws Exception {
        MockMvc mvc = mvcFor(new SessionController(null, null, null));

        mvc.perform(post("/api/sessions").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").exists());
    }

    @Test
    void createReturnsWeightsAndJdFileKeyInResponse() throws Exception {
        SessionService service = new SessionService(
                savingSessionRepository(), null, null,
                userRepositoryReturning(user()), null);
        MockMvc mvc = mvcFor(new SessionController(service, countingCandidateRepository(), null));

        mvc.perform(post("/api/sessions").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"T","description":"d","jobTitle":"E","positionCode":"BE-1","requiredSkills":["Java"],"weights":{
                                 "technicalSkills":30,"experience":25,"education":15,
                                 "softSkills":20,"cultureFit":10}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session created"))
                .andExpect(jsonPath("$.data.weights.technicalSkills").value(30.0))
                .andExpect(jsonPath("$.data.weights.cultureFit").value(10.0))
                .andExpect(jsonPath("$.data.jdFileKey").hasJsonPath())
                .andExpect(jsonPath("$.data.shortlistedCount").value(4));
    }

    @Test
    void createSessionCascadesWeightsOntoSavedEntity() {
        SessionService service = new SessionService(
                savingSessionRepository(), null, null,
                userRepositoryReturning(user()), null);
        CreateSessionRequest request = new CreateSessionRequest();
        request.setTitle("T");
        request.setWeights(com.resumescreener.common.session.WeightsDto.builder()
                .technicalSkills(30.0).experience(25.0).education(15.0)
                .softSkills(20.0).cultureFit(10.0).build());

        ScreeningSession saved = service.createSession(request, "recruiter@example.com");

        ScoringWeights weights = saved.getWeights();
        assertThat(weights).isNotNull();
        assertThat(weights.getSession()).isSameAs(saved);
        assertThat(weights.getTechnicalSkills()).isEqualTo(30.0);
    }

    private static final String JOB = "\"jobTitle\":\"Senior Backend Engineer\",\"positionCode\":\"be-104\","
            + "\"jobDescription\":\"Build APIs\",\"requiredSkills\":[\"Java\",\"Spring\",\"SQL\"]";

    private MockMvc savingMvc() {
        SessionService service = new SessionService(
                savingSessionRepository(), null, null,
                userRepositoryReturning(user()), null);
        return mvcFor(new SessionController(service, countingCandidateRepository(), null));
    }

    @Test
    void createRejectsMissingPositionCodeWith400() throws Exception {
        savingMvc().perform(post("/api/sessions").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"jobTitle\":\"Engineer\",\"requiredSkills\":[\"Java\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").value(org.hamcrest.Matchers.startsWith("positionCode")));
    }

    @Test
    void createRejectsThirtyOneSkills() throws Exception {
        String skills = java.util.stream.IntStream.rangeClosed(1, 31).mapToObj(i -> "\"S" + i + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        savingMvc().perform(post("/api/sessions").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"jobTitle\":\"E\",\"positionCode\":\"BE-1\",\"requiredSkills\":[" + skills + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details").value(org.hamcrest.Matchers.startsWith("requiredSkills")));
    }

    @Test
    void createRejectsSkillRepeatedIgnoringCase() throws Exception {
        savingMvc().perform(post("/api/sessions").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"jobTitle\":\"E\",\"positionCode\":\"BE-1\",\"requiredSkills\":[\"Java\",\" java \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That skill is already on the list"));
    }

    @Test
    void createUppercasesPositionCodeAndReturnsJob() throws Exception {
        savingMvc().perform(post("/api/sessions").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\"," + JOB + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.data.job.positionCode").value("BE-104"))
                .andExpect(jsonPath("$.data.job.description").value("Build APIs"))
                .andExpect(jsonPath("$.data.job.requiredSkills[1]").value("Spring"));
    }

    @Test
    void patchWithoutTitleIsAccepted() throws Exception {
        ScreeningSession existing = ScreeningSession.builder().id("session-1").title("Old").jobTitle("Old")
                .user(user()).status(SessionStatus.PENDING).build();
        SessionService service = new SessionService(sessionRepositoryHolding(existing), null, null,
                userRepositoryReturning(user()), null);
        MockMvc mvc = mvcFor(new SessionController(service, countingCandidateRepository(), null));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/sessions/session-1")
                        .principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requiredSkills\":[\"Kafka\",\"Java\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job.title").value("Old"))
                .andExpect(jsonPath("$.data.job.requiredSkills[0]").value("Kafka"));
    }

    @Test
    void patchRejectsInvalidPositionCode() throws Exception {
        savingMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/sessions/session-1")
                        .principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\"BE 104\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details").value(org.hamcrest.Matchers.startsWith("positionCode")));
    }

    private static ScreeningSessionRepository sessionRepositoryHolding(ScreeningSession session) {
        return (ScreeningSessionRepository) Proxy.newProxyInstance(
                ScreeningSessionRepository.class.getClassLoader(),
                new Class<?>[]{ScreeningSessionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findWithWeightsById" -> Optional.of(session);
                    case "save" -> args[0];
                    default -> null;
                });
    }

    private MockMvc mvcFor(SessionController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("recruiter@example.com");
        return user;
    }

    private static UserRepository userRepositoryReturning(User user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) ->
                        "findByEmailIgnoreCase".equals(method.getName()) ? Optional.of(user) : null
        );
    }

    private static ScreeningSessionRepository savingSessionRepository() {
        return (ScreeningSessionRepository) Proxy.newProxyInstance(
                ScreeningSessionRepository.class.getClassLoader(),
                new Class<?>[]{ScreeningSessionRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        ScreeningSession session = (ScreeningSession) args[0];
                        session.setId("session-1");
                        session.setStatus(SessionStatus.PENDING);
                        return session;
                    }
                    return null;
                }
        );
    }

    private static CandidateRepository countingCandidateRepository() {
        return (CandidateRepository) Proxy.newProxyInstance(
                CandidateRepository.class.getClassLoader(),
                new Class<?>[]{CandidateRepository.class},
                (proxy, method, args) ->
                        "countBySessionId".equals(method.getName()) ? 0L
                                : "countBySessionIdAndReviewStatusNot".equals(method.getName()) ? 4L : null
        );
    }
}
