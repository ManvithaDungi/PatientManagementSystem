package com.securehealth.backend.model;

/**
 * Enumeration of authorized user roles within the system.
 * <p>
 * Used for Role-Based Access Control (RBAC) to restrict access to 
 * specific API endpoints and application features.
 * </p>
 */
public enum Role {
    PATIENT,
    DOCTOR,
    NURSE,
    ADMIN,
    LAB_TECHNICIAN
}