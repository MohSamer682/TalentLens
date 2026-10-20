package com.resumescreener.api.portal;

import com.resumescreener.api.support.ApiIntegrationTest;
import com.resumescreener.common.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A candidate token may reach the portal, its own profile and auth, and nothing else, including endpoints added later. */
class CandidateIsolationTest extends ApiIntegrationTest {

    private static final List<String> CANDIDATE_ROUTES = List.of("/api/portal/", "/api/me/", "/api/auth/", "/api/client-errors", "/error");

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @Test
    void candidateTokenIsRefusedOnEveryStaffEndpoint() throws Exception {
        String token = bearer(user(Role.CANDIDATE));
        List<String> checked = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/") || CANDIDATE_ROUTES.stream().anyMatch(pattern::startsWith)) continue;
                String url = pattern.replaceAll("\\{[^}]+}", "x");
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    mvc.perform(request(HttpMethod.valueOf(method.name()), url).header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
                    checked.add(method + " " + url);
                }
            }
        }
        assertThat(checked).hasSizeGreaterThan(40).contains("GET /api/sessions", "GET /api/admin/users", "POST /api/sessions/x/candidates/x/portal/invites");
    }

    @Test
    void candidateCanStillReadThemselves() throws Exception {
        String token = bearer(user(Role.CANDIDATE));
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void staffTokenCannotCallPortalEndpoints() throws Exception {
        mvc.perform(post("/api/portal/invites/x/claim").header("Authorization", bearer(user(Role.RECRUITER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/portal/invites/x/claim").header("Authorization", bearer(user(Role.ADMIN))))
                .andExpect(status().isForbidden());
    }
}
