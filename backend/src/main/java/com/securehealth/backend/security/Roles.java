package com.securehealth.backend.security;

import com.securehealth.backend.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the application role of an {@link Authentication} by matching its authorities against the
 * known {@link Role} names, instead of trusting whichever authority happens to be listed first.
 */
public final class Roles {

    private static final Set<String> KNOWN = Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toSet());

    private Roles() {
    }

    /** The caller's role name, or {@code "UNKNOWN"} when no known role authority is present. */
    public static String of(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return "UNKNOWN";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(KNOWN::contains)
                .findFirst()
                .orElse("UNKNOWN");
    }
}
