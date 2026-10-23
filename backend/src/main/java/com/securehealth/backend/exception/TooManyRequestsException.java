package com.securehealth.backend.exception;

/** The caller exceeded a rate limit (HTTP 429). */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
