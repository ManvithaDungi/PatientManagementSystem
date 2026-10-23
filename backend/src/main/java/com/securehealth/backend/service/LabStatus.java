package com.securehealth.backend.service;

import java.util.List;

/** Canonical lab-order status vocabulary (matches the lab technician UI) and allowed transitions. */
public final class LabStatus {
    public static final String PENDING = "Pending";
    public static final String COLLECTED = "Collected";
    public static final String RESULTS_PENDING = "Results Pending";
    public static final String COMPLETED = "Completed";
    public static final List<String> ALL = List.of(PENDING, COLLECTED, RESULTS_PENDING, COMPLETED);

    private LabStatus() {}

    /** Returns the canonical spelling of a status (case/underscore-insensitive) or null if unknown. */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String norm = raw.trim().replace('_', ' ');
        for (String s : ALL) {
            if (s.equalsIgnoreCase(norm)) return s;
        }
        return null;
    }

    public static boolean canTransition(String from, String to) {
        String f = canonical(from);
        if (f == null) f = PENDING;
        if (f.equals(to)) return true;
        return ALL.indexOf(to) > ALL.indexOf(f) && !COMPLETED.equals(f);
    }
}
