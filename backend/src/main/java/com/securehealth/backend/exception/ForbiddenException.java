package com.securehealth.backend.exception;

/** The caller is authenticated but not permitted (HTTP 403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
