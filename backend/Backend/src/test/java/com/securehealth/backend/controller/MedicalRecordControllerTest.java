package com.securehealth.backend.controller;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.MedicalRecord;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.MedicalRecordRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import com.securehealth.backend.service.MedicalRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicalRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MedicalRecordControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private MedicalRecordRepository medicalRecordRepository;
    @MockBean private PatientAccessValidator accessValidator;
    @MockBean private MedicalRecordService medicalRecordService;

    @MockBean private com.securehealth.backend.util.JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;
    @MockBean private AuditLogRepository auditLogRepository;

    private MedicalRecord recordFor(Long profileId) {
        Login patientLogin = new Login();
        patientLogin.setEmail("patient@mail.com");
        PatientProfile patient = new PatientProfile();
        patient.setProfileId(profileId);
        patient.setUser(patientLogin);

        MedicalRecord record = new MedicalRecord();
        record.setRecordId(5L);
        record.setPatient(patient);
        record.setDiagnosis("Flu");
        return record;
    }

    @Test
    void getById_NotFound_Returns404() throws Exception {
        when(medicalRecordRepository.findById(99L)).thenReturn(Optional.empty());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "doctor@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(get("/api/medical-records/99").principal(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_AccessGranted_Returns200() throws Exception {
        when(medicalRecordRepository.findById(5L)).thenReturn(Optional.of(recordFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/medical-records/5").principal(auth))
                .andExpect(status().isOk());
    }

    @Test
    void getByPatient_RequestsMedicalRecordsConsent() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.stranger@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for MEDICAL_RECORDS"))
                .when(accessValidator).validateAccess(1L, auth, "MEDICAL_RECORDS");

        mockMvc.perform(get("/api/medical-records/patient/1").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_AccessDenied_Returns403() throws Exception {
        when(medicalRecordRepository.findById(5L)).thenReturn(Optional.of(recordFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.stranger@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for MEDICAL_RECORDS"))
                .when(accessValidator).validateAccess(1L, auth, "MEDICAL_RECORDS");

        mockMvc.perform(get("/api/medical-records/5").principal(auth))
                .andExpect(status().isForbidden());
    }
}
