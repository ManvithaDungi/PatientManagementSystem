package com.securehealth.backend.controller;

import com.securehealth.backend.dto.AcceptInviteRequest;
import com.securehealth.backend.dto.StaffInviteRequest;
import com.securehealth.backend.model.StaffInvite;
import com.securehealth.backend.service.StaffInviteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Staff onboarding endpoints.
 * <ul>
 *   <li>{@code /api/admin/staff/invites/**} — ADMIN only (also enforced by SecurityConfig).</li>
 *   <li>{@code /api/auth/invite/**} — public, authorised solely by possession of the invite token.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class StaffInviteController {

    @Autowired
    private StaffInviteService inviteService;

    @Autowired
    private com.securehealth.backend.service.RateLimiterService rateLimiterService;

    // ---------------------------------------------------------------- admin

    @PostMapping("/admin/staff/invites")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> createInvite(@Valid @RequestBody StaffInviteRequest request, Authentication auth) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(inviteService.createInvite(request.getEmail(), request.getRole(), auth.getName()));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/admin/staff/invites")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> listInvites() {
        return ResponseEntity.ok(inviteService.listPending());
    }

    @DeleteMapping("/admin/staff/invites/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> revokeInvite(@PathVariable Long id, Authentication auth) {
        try {
            inviteService.revoke(id, auth.getName());
            return ResponseEntity.ok(Map.of("message", "Invite revoked."));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    // --------------------------------------------------------------- public

    @GetMapping("/auth/invite/validate")
    public ResponseEntity<?> validate(@RequestParam("token") String token,
                                      jakarta.servlet.http.HttpServletRequest httpRequest) {
        rateLimiterService.checkIpLimit("invite-validate", httpRequest.getRemoteAddr(), 30, java.time.Duration.ofMinutes(10));
        try {
            StaffInvite invite = inviteService.requireUsable(token);
            Map<String, Object> body = new HashMap<>();
            body.put("valid", true);
            body.put("email", invite.getEmail());
            body.put("role", invite.getRole().name());
            return ResponseEntity.ok(body);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/auth/invite/accept")
    public ResponseEntity<?> accept(@Valid @RequestBody AcceptInviteRequest request) {
        try {
            inviteService.accept(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Account created. You can now sign in."));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = e instanceof IllegalStateException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        if (e.getMessage() != null && e.getMessage().startsWith("404")) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
}
