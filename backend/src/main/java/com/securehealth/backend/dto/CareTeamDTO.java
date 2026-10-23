package com.securehealth.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Treating doctor / nurse (login user ids) of a patient; either may be null. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareTeamDTO {
    private Long patientId;
    private Long assignedDoctorId;
    private Long assignedNurseId;
}
