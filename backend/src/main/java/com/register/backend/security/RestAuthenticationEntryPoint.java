package com.register.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes a 401 response in the API's standard {@link ErrorResponse} JSON shape whenever an unauthenticated
 * request is rejected by Spring Security (e.g. no token, or an invalid/expired token, on a protected
 * {@code /api/admin/**} endpoint).
 *
 * <p>This runs inside the security filter chain, before {@code DispatcherServlet}/
 * {@code @RestControllerAdvice} ever see the request — so without this, the response would be Spring
 * Security's own default 401 body instead of matching the rest of the API.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Creates the entry point.
     *
     * @param objectMapper used to serialize the {@link ErrorResponse} body
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the 401 error body for an unauthenticated request.
     *
     * @param request       the rejected request
     * @param response      the response to write to
     * @param authException the authentication failure that triggered this entry point
     * @throws IOException if writing the response body fails
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication required",
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

}
