package com.securehealth.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CALYX-029: the role is the known role authority, not whichever authority happens to come first. */
class RolesTest {

    @Test
    void picksTheKnownRoleEvenWhenAnotherAuthorityIsListedFirst() {
        var auth = new UsernamePasswordAuthenticationToken("u", null,
                List.of(new SimpleGrantedAuthority("SCOPE_read"), new SimpleGrantedAuthority("DOCTOR")));
        assertEquals("DOCTOR", Roles.of(auth));
    }

    @Test
    void unknownWhenNoKnownRoleIsPresent() {
        assertEquals("UNKNOWN", Roles.of(new UsernamePasswordAuthenticationToken("u", null, List.of(new SimpleGrantedAuthority("X")))));
        assertEquals("UNKNOWN", Roles.of(null));
    }
}
