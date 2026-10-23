package com.securehealth.backend.exception;

/** The request is invalid or violates a business rule (HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
