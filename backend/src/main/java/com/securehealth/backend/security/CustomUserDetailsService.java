package com.securehealth.backend.security;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.repository.LoginRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Custom implementation of {@link UserDetailsService} to load user-specific data.
 * <p>
 * Bridges the application's {@link Login} entity with Spring Security's 
 * {@link UserDetails} for authentication and authority mapping.
 * </p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private LoginRepository loginRepository;

    /**
     * Locates the user based on the email (username).
     *
     * @param email the email identifying the user whose data is required
     * @return a fully populated {@link UserDetails} object
     * @throws UsernameNotFoundException if the user could not be found
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Find User
        Login user = loginRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // 2. Convert to Spring Security User
        // Note: We don't add "ROLE_" prefix here because your DB stores "ADMIN", "DOCTOR", etc.
        // We handle the authority mapping directly.
        return new User(
                user.getEmail(),
                user.getPasswordHash(),
                !Boolean.TRUE.equals(user.getArchived()),   // enabled
                true,                                       // account non-expired
                true,                                       // credentials non-expired
                !user.isLocked(),                           // account non-locked
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name())));
    }
}