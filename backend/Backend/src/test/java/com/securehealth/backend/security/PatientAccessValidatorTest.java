package com.securehealth.backend.security;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.service.ConsentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PatientAccessValidatorTest {

    @Mock
    private PatientProfileRepository patientProfileRepository;

    @Mock
    private LoginRepository loginRepository;

    @Mock
    private ConsentService consentService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PatientAccessValidator validator;

    private PatientProfile mockProfile;

    @BeforeEach
    void setUp() {
        Login mockLogin = new Login();
        mockLogin.setEmail("patientA@mail.com");

        mockProfile = new PatientProfile();
        mockProfile.setProfileId(1L);
        mockProfile.setUser(mockLogin);
    }

    @Test
    void validateAccess_DoctorBypassesCheck() {
        // Arrange
        GrantedAuthority authority = new SimpleGrantedAuthority("DOCTOR");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateAccess(1L, authentication));
        
        // Verify repository was never called because Doctors bypass the check
        verify(patientProfileRepository, never()).findById(anyLong());
    }

    @Test
    void validateAccess_PatientAccessingOwnData_Succeeds() {
        // Arrange
        GrantedAuthority authority = new SimpleGrantedAuthority("PATIENT");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("patientA@mail.com");
        when(patientProfileRepository.findById(1L)).thenReturn(Optional.of(mockProfile));

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateAccess(1L, authentication));
    }

    @Test
    void validateAccess_PatientAccessingOtherData_ThrowsException() {
        // Arrange
        GrantedAuthority authority = new SimpleGrantedAuthority("PATIENT");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("hacker@mail.com"); // Different email!
        when(patientProfileRepository.findById(1L)).thenReturn(Optional.of(mockProfile));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> validator.validateAccess(1L, authentication));

        assertTrue(exception.getMessage().contains("403 Forbidden"));
    }

    @Test
    void validateAccess_AdminBypassesConsentCheck() {
        GrantedAuthority authority = new SimpleGrantedAuthority("ADMIN");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();

        assertDoesNotThrow(() -> validator.validateAccess(1L, authentication, "MEDICAL_RECORDS"));

        verify(consentService, never()).hasConsent(anyLong(), anyLong(), anyString());
    }

    @Test
    void validateAccess_DoctorWithActiveConsent_Succeeds() {
        GrantedAuthority authority = new SimpleGrantedAuthority("DOCTOR");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("dr.house@mail.com");

        Login doctorLogin = new Login();
        doctorLogin.setUserId(9L);
        doctorLogin.setEmail("dr.house@mail.com");
        when(loginRepository.findByEmail("dr.house@mail.com")).thenReturn(Optional.of(doctorLogin));
        when(consentService.hasConsent(1L, 9L, "MEDICAL_RECORDS")).thenReturn(true);

        assertDoesNotThrow(() -> validator.validateAccess(1L, authentication, "MEDICAL_RECORDS"));
    }

    @Test
    void validateAccess_DoctorWithoutConsent_ThrowsException() {
        GrantedAuthority authority = new SimpleGrantedAuthority("DOCTOR");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("dr.house@mail.com");

        Login doctorLogin = new Login();
        doctorLogin.setUserId(9L);
        doctorLogin.setEmail("dr.house@mail.com");
        when(loginRepository.findByEmail("dr.house@mail.com")).thenReturn(Optional.of(doctorLogin));
        when(consentService.hasConsent(1L, 9L, "MEDICAL_RECORDS")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> validator.validateAccess(1L, authentication, "MEDICAL_RECORDS"));

        assertTrue(exception.getMessage().contains("403 Forbidden"));
    }

    @Test
    void validateAccess_NoConsentTypeRequested_SkipsConsentCheck() {
        // Appointments and other non-consent-gated categories use the 2-arg overload,
        // which must never touch ConsentService.
        GrantedAuthority authority = new SimpleGrantedAuthority("DOCTOR");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();

        assertDoesNotThrow(() -> validator.validateAccess(1L, authentication));

        verifyNoInteractions(consentService);
    }
}