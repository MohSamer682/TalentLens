package com.resumescreener.api.common;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void bodyStillReachesTheClientAfterLogging() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
                res.setStatus(400);
                res.setContentType("application/json");
                res.getWriter().write("{\"success\":false}");
            }
        });

        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":false}");
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void nonApiPathsPassThroughUnwrapped() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, chain);

        assertThat(chain.getResponse()).isSameAs(response);
    }

    @Test
    void inviteTokensNeverReachTheLog() {
        assertThat(RequestIdFilter.loggablePath("/api/portal/invites/abcDEF123_-xyz/accept")).isEqualTo("/api/portal/invites/{token}/accept");
        assertThat(RequestIdFilter.loggablePath("/api/portal/invites/abcDEF123_-xyz")).isEqualTo("/api/portal/invites/{token}");
        assertThat(RequestIdFilter.loggablePath("/api/sessions/s1")).isEqualTo("/api/sessions/s1");
    }
}
