package com.register.backend.exception;

/**
 * Thrown when a login attempt supplies a username that doesn't exist or a password that doesn't match.
 * Deliberately carries the same message regardless of which check failed, so the API never reveals
 * whether a given username exists.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }

}
