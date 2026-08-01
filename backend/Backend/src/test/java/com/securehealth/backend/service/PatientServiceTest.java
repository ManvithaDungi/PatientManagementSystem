package com.securehealth.backend.service;

import com.securehealth.backend.dto.PatientDTO;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PatientServiceTest {

    @Mock private LoginRepository loginRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ConsentService consentService;

    @InjectMocks private PatientService patientService;

    private Login doctorLogin;
    private PatientProfile patientProfile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        doctorLogin = new Login();
        doctorLogin.setUserId(2L);
        doctorLogin.setEmail("dr.house@mail.com");

        Login patientUser = new Login();
        patientUser.setEmail("patient@mail.com");

        patientProfile = new PatientProfile();
        patientProfile.setProfileId(1L);
        patientProfile.setFirstName("John");
        patientProfile.setLastName("Doe");
        patientProfile.setUser(patientUser);
    }

    @Test
    void getPatientsByDoctor_AsOwningDoctor_ReturnsList() {
        // Arrange
        when(loginRepository.findById(2L)).thenReturn(Optional.of(doctorLogin));
        when(appointmentRepository.findDistinctPatientsByDoctorId(2L))
                .thenReturn(Collections.singletonList(patientProfile));

        // Act
        List<PatientDTO> result = patientService.getPatientsByDoctor(2L, "dr.house@mail.com", "DOCTOR");

        // Assert
        assertEquals(1, result.size());
        assertEquals("John", result.get(0).getFirstName());
    }

    @Test
    void getPatientsByDoctor_AsAdmin_ReturnsList() {
        // Arrange
        when(loginRepository.findById(2L)).thenReturn(Optional.of(doctorLogin));
        when(appointmentRepository.findDistinctPatientsByDoctorId(2L))
                .thenReturn(Collections.singletonList(patientProfile));

        // Act - Requesting as an Admin with a different email
        List<PatientDTO> result = patientService.getPatientsByDoctor(2L, "admin@hospital.com", "ADMIN");

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void getPatientsByDoctor_AsDifferentDoctor_Throws403() {
        // Arrange
        when(loginRepository.findById(2L)).thenReturn(Optional.of(doctorLogin));

        // Act & Assert - Requesting as a different doctor
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> patientService.getPatientsByDoctor(2L, "dr.strange@mail.com", "DOCTOR"));

        assertTrue(exception.getMessage().contains("403 Forbidden"));
    }

    @Test
    void getPatientById_AsAssignedDoctor_SkipsConsentCheck() {
        patientProfile.setAssignedDoctor(doctorLogin);
        when(patientProfileRepository.findById(1L)).thenReturn(Optional.of(patientProfile));

        PatientDTO result = patientService.getPatientById(1L, "dr.house@mail.com", "DOCTOR");

        assertEquals("John", result.getFirstName());
        verifyNoInteractions(consentService);
    }

    @Test
    void getPatientById_AsNonAssignedDoctorWithConsent_ReturnsProfile() {
        when(patientProfileRepository.findById(1L)).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findByEmail("dr.strange@mail.com")).thenReturn(Optional.of(
                loginWith(9L, "dr.strange@mail.com")));
        when(consentService.hasConsent(1L, 9L, "ALL")).thenReturn(true);

        PatientDTO result = patientService.getPatientById(1L, "dr.strange@mail.com", "DOCTOR");

        assertEquals("John", result.getFirstName());
    }

    @Test
    void getPatientById_AsNonAssignedDoctorWithoutConsent_Throws403() {
        when(patientProfileRepository.findById(1L)).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findByEmail("dr.strange@mail.com")).thenReturn(Optional.of(
                loginWith(9L, "dr.strange@mail.com")));
        when(consentService.hasConsent(1L, 9L, "ALL")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> patientService.getPatientById(1L, "dr.strange@mail.com", "DOCTOR"));

        assertTrue(exception.getMessage().contains("403 Forbidden"));
    }

    @Test
    void getPatientById_AsAdmin_SkipsConsentCheck() {
        when(patientProfileRepository.findById(1L)).thenReturn(Optional.of(patientProfile));

        PatientDTO result = patientService.getPatientById(1L, "admin@mail.com", "ADMIN");

        assertEquals("John", result.getFirstName());
        verifyNoInteractions(consentService);
    }

    private Login loginWith(Long userId, String email) {
        Login login = new Login();
        login.setUserId(userId);
        login.setEmail(email);
        return login;
    }
}