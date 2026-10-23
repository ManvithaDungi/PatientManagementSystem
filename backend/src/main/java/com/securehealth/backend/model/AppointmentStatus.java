package com.securehealth.backend.model;

/**
 * Enumeration of possible appointment statuses.
 */
public enum AppointmentStatus {
    PENDING_APPROVAL,
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    REJECTED;

    /**
     * Allowed lifecycle: PENDING_APPROVAL -> SCHEDULED | REJECTED | CANCELLED;
     * SCHEDULED -> COMPLETED | CANCELLED | NO_SHOW. COMPLETED, CANCELLED, REJECTED and NO_SHOW are final.
     */
    public boolean canTransitionTo(AppointmentStatus next) {
        switch (this) {
            case PENDING_APPROVAL:
                return next == SCHEDULED || next == REJECTED || next == CANCELLED;
            case SCHEDULED:
                return next == COMPLETED || next == CANCELLED || next == NO_SHOW;
            default:
                return false;
        }
    }
}
