package com.securehealth.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LabStatusTest {

    @Test
    void canonical_isCaseAndUnderscoreInsensitive() {
        assertEquals("Pending", LabStatus.canonical("PENDING"));
        assertEquals("Results Pending", LabStatus.canonical("results_pending"));
        assertEquals("Completed", LabStatus.canonical("completed"));
        assertNull(LabStatus.canonical("bogus"));
        assertNull(LabStatus.canonical(null));
    }

    @Test
    void transitions_moveForwardOnly_andCompletedIsFinal() {
        assertTrue(LabStatus.canTransition("PENDING", "Collected"));
        assertTrue(LabStatus.canTransition("Collected", "Completed"));
        assertFalse(LabStatus.canTransition("Collected", "Pending"));
        assertFalse(LabStatus.canTransition("Completed", "Collected"));
        assertTrue(LabStatus.canTransition(null, "Collected"));
    }
}
