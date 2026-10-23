package com.securehealth.backend.exception;

/** The request conflicts with current state (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
