package com.register.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        LocalDateTime timestamp,
        String path
) {

    public ErrorResponse(int status, String message, String path) {
        this(status, message, null, LocalDateTime.now(), path);
    }

    public ErrorResponse(int status, String message, Map<String, String> errors, String path) {
        this(status, message, errors, LocalDateTime.now(), path);
    }

}
