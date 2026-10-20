package com.resumescreener.api.auth;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Someone signed in with a temporary password can only read who they are and set a new one. */
public class PasswordChangeGate extends OncePerRequestFilter {

    public static final String MARKER = "MUST_CHANGE_PASSWORD";

    private static final Set<String> ALLOWED = Set.of("GET /api/auth/me", "GET /api/me/profile", "PUT /api/me/password");

    /** Signing in or up starts over, whatever token the browser still sends; opening an access link needs no account. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/api/auth/login") || uri.equals("/api/auth/register")
                || uri.equals("/api/portal/invites/preview") || uri.equals("/api/portal/invites/accept");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean flagged = auth != null && auth.getAuthorities().stream().anyMatch(a -> MARKER.equals(a.getAuthority()));
        if (flagged && !ALLOWED.contains(request.getMethod() + " " + request.getRequestURI())) {
            ErrorResponses.write(response, HttpStatus.FORBIDDEN, ApiErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "Choose a new password to continue.", "The account has a temporary password");
            return;
        }
        chain.doFilter(request, response);
    }
}
