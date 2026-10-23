package com.securehealth.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CALYX-039: routine reads must not write an audit row; mutations and denied/failed calls still do. */
class RequestLoggingPolicyTest {

    @Test
    void successfulReadsAreNotAudited() {
        assertFalse(RequestLoggingFilter.shouldAudit("GET", 200));
        assertFalse(RequestLoggingFilter.shouldAudit("GET", 404));
        assertFalse(RequestLoggingFilter.shouldAudit("HEAD", 200));
    }

    @Test
    void mutationsAreAuditedWhateverTheOutcome() {
        for (String m : new String[]{"POST", "PUT", "PATCH", "DELETE", "post"}) {
            assertTrue(RequestLoggingFilter.shouldAudit(m, 200), m);
            assertTrue(RequestLoggingFilter.shouldAudit(m, 400), m);
        }
    }

    @Test
    void deniedAndFailedReadsAreAudited() {
        assertTrue(RequestLoggingFilter.shouldAudit("GET", 403));
        assertTrue(RequestLoggingFilter.shouldAudit("GET", 500));
    }
}
