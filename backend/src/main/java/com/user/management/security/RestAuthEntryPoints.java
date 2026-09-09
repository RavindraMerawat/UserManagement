package com.user.management.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Security failures happen inside the filter chain, where {@code @RestControllerAdvice}
 * never sees them. These two handlers emit the same JSON envelope as
 * {@code GlobalExceptionHandler} so the UI can read every error the same way.
 */
public final class RestAuthEntryPoints {

    private RestAuthEntryPoints() {
    }

    /** No usable credentials on the request: 401. */
    @Component
    public static class Unauthorized implements AuthenticationEntryPoint {

        private final ObjectMapper mapper;

        public Unauthorized(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void commence(HttpServletRequest request,
                             HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            write(mapper, response, request, HttpStatus.UNAUTHORIZED,
                    "Please sign in to use this endpoint");
        }
    }

    /**
     * Signed in, but the role is not allowed here: 403. Without this bean Spring
     * Security falls back to the authentication entry point for some denials, which
     * would report an authorization failure as a 401.
     */
    @Component
    public static class Forbidden implements AccessDeniedHandler {

        private final ObjectMapper mapper;

        public Forbidden(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void handle(HttpServletRequest request,
                           HttpServletResponse response,
                           AccessDeniedException deniedException) throws IOException {
            String role = SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication()
                            .getPrincipal() instanceof AppUserPrincipal principal
                    ? principal.getRole().getDisplayName()
                    : "your";
            write(mapper, response, request, HttpStatus.FORBIDDEN,
                    "This action is not available for " + role + " accounts");
        }
    }

    private static void write(ObjectMapper mapper,
                              HttpServletResponse response,
                              HttpServletRequest request,
                              HttpStatus status,
                              String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());

        mapper.writeValue(response.getOutputStream(), body);
    }
}
