package com.securehealth.backend.service;

import com.securehealth.backend.exception.BadRequestException;

/** Manual length checks for partial-update payloads (bean validation would reject legitimately absent fields). */
final class Limits {
    private Limits() {
    }

    static void max(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new BadRequestException(field + " must be at most " + max + " characters");
        }
    }
}
