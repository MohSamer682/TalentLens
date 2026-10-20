package com.resumescreener.api.portal;

import com.resumescreener.api.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.portal.invite-requests-per-minute=2")
class InviteThrottleApiTest extends ApiIntegrationTest {

    private ResultActions preview(String address) throws Exception {
        return mvc.perform(post("/api/portal/invites/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"guess\"}").with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                }));
    }

    @Test
    void guessingFromOneAddressIsCutOffWithoutAffectingOthers() throws Exception {
        preview("203.0.113.7").andExpect(status().isGone());
        preview("203.0.113.7").andExpect(status().isGone());
        preview("203.0.113.7").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        preview("198.51.100.4").andExpect(status().isGone());
    }
}
