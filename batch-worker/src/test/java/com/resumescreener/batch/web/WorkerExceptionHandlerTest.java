package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new JobTriggerController(mock(JobService.class), mock(LlmStatusService.class)))
            .setControllerAdvice(new WorkerExceptionHandler())
            .build();

    @Test
    void missingSessionIdIs400WithEnvelope() throws Exception {
        mvc.perform(post("/internal/jobs/start").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedJsonIs400WithEnvelope() throws Exception {
        mvc.perform(post("/internal/jobs/start").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details").value("Request body could not be parsed as JSON"));
    }

    @Test
    void wrongMethodIs405WithEnvelope() throws Exception {
        mvc.perform(delete("/internal/jobs/start"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void acceptedJobAnswersWithEnvelope() throws Exception {
        mvc.perform(post("/internal/jobs/start").contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":\"s1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("s1"));
    }
}
