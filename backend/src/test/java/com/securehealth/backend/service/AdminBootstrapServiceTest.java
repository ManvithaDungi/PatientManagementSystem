package com.securehealth.backend.service;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.LoginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock private LoginRepository loginRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthService authService;

    @InjectMocks private AdminBootstrapService bootstrap;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bootstrap, "adminEmail", "first.admin@hospital.test");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "Unusual-Phrase-4471");
    }

    @Test
    void createsFirstAdminWhenNoneExists() {
        when(loginRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(passwordEncoder.encode("Unusual-Phrase-4471")).thenReturn("hashed");

        bootstrap.run(null);

        ArgumentCaptor<Login> saved = ArgumentCaptor.forClass(Login.class);
        verify(loginRepository).save(saved.capture());
        assertEquals("first.admin@hospital.test", saved.getValue().getEmail());
        assertEquals(Role.ADMIN, saved.getValue().getRole());
        assertEquals("hashed", saved.getValue().getPasswordHash());
        assertTrue(saved.getValue().isTwoFactorEnabled());
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(loginRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        bootstrap.run(null);

        verify(loginRepository, never()).save(any());
    }

    @Test
    void doesNothingWithoutCredentials() {
        ReflectionTestUtils.setField(bootstrap, "adminPassword", "");

        bootstrap.run(null);

        verifyNoInteractions(loginRepository);
    }

    @Test
    void weakPasswordAbortsStartup() {
        when(loginRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        doThrow(new RuntimeException("Password contains a common weak pattern"))
                .when(authService).validatePasswordStrength("Unusual-Phrase-4471");

        assertThrows(RuntimeException.class, () -> bootstrap.run(null));
        verify(loginRepository, never()).save(any());
    }
}
