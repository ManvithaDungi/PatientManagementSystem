package com.securehealth.backend.dto;

import lombok.Data;

/**
 * Data Transfer Object for administrative dashboard metrics.
 * <p>
 * Contains aggregated counts for patients, doctors, today's appointments, 
 * and pending appointment approvals.
 * </p>
 */
@Data
public class AdminMetricsDTO {
    private long totalPatients;
    private long totalDoctors;
    private long todaysAppointments;
    private long pendingApprovals;
}