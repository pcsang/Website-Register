package com.register.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.register.backend.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes a 403 response in the API's standard {@link ErrorResponse} JSON shape whenever an authenticated
 * request is rejected for lacking the required role (e.g. a valid token without {@code ROLE_ADMIN} calling
 * a protected {@code /api/admin/**} endpoint).
 *
 * <p>Like {@link RestAuthenticationEntryPoint}, this runs inside the security filter chain, so it must
 * write the shared error shape itself rather than relying on {@code GlobalExceptionHandler}.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Creates the handler.
     *
     * @param objectMapper used to serialize the {@link ErrorResponse} body
     */
    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the 403 error body for a request rejected due to insufficient authority.
     *
     * @param request               the rejected request
     * @param response              the response to write to
     * @param accessDeniedException the authorization failure that triggered this handler
     * @throws IOException if writing the response body fails
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

}
