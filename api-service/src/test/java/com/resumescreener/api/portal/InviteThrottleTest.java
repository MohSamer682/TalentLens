package com.resumescreener.api.portal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InviteThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final InviteThrottle throttle = new InviteThrottle(3, clock);

    private int call(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portal/invites/preview");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        throttle.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void theFourthCallInAMinuteFromOneAddressIs429() throws Exception {
        for (int i = 0; i < 3; i++) assertThat(call("10.0.0.1")).isEqualTo(200);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portal/invites/preview");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        throttle.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(call("10.0.0.2")).isEqualTo(200);
    }

    @Test
    void theWindowResetsAfterAMinute() throws Exception {
        for (int i = 0; i < 3; i++) call("10.0.0.1");
        now.set(now.get().plus(Duration.ofSeconds(61)));
        assertThat(call("10.0.0.1")).isEqualTo(200);
    }

    @Test
    void otherPathsAreNotCounted() throws Exception {
        assertThat(throttle.shouldNotFilter(new MockHttpServletRequest("GET", "/api/sessions"))).isTrue();
        assertThat(throttle.shouldNotFilter(new MockHttpServletRequest("POST", "/api/portal/invites/accept"))).isFalse();
    }

    @Test
    void trackedAddressesStayUnderTheCap() throws Exception {
        InviteThrottle small = new InviteThrottle(3, clock, 2);
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portal/invites/preview");
            request.setRemoteAddr("10.0.1." + i);
            small.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(small.tracked()).isLessThanOrEqualTo(2);
    }
}
