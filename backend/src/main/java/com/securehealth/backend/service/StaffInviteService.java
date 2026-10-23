package com.securehealth.backend.service;

import com.securehealth.backend.dto.AcceptInviteRequest;
import com.securehealth.backend.dto.StaffInviteDTO;
import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.model.DoctorProfile;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.model.StaffInvite;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.DoctorProfileRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.StaffInviteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Staff onboarding via admin-issued, single-use, expiring invite links.
 * <p>
 * Public self-registration only creates patients; every other role can only
 * come into existence through {@link #createInvite} followed by {@link #accept}.
 * </p>
 */
@Service
public class StaffInviteService {

    /** How long an invite link stays valid. */
    public static final int INVITE_VALID_HOURS = 72;

    private static final String GENERIC_INVALID = "This invitation is invalid or has expired.";

    @Autowired private StaffInviteRepository inviteRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private DoctorProfileRepository doctorProfileRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;
    @Autowired private AuthService authService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Creates an invite and emails the link. Any earlier unused invite for the
     * same email is revoked so only the newest link works.
     */
    @Transactional
    public StaffInviteDTO createInvite(String email, Role role, String adminEmail) {
        if (role == null || role == Role.PATIENT) {
            throw new IllegalArgumentException("Invites can only be issued for staff roles.");
        }
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        if (loginRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("An account with this email already exists.");
        }

        inviteRepository.findByEmailIgnoreCaseAndUsedFalseAndRevokedFalse(normalizedEmail)
                .forEach(old -> {
                    old.setRevoked(true);
                    inviteRepository.save(old);
                });

        String rawToken = generateToken();

        StaffInvite invite = new StaffInvite();
        invite.setEmail(normalizedEmail);
        invite.setRole(role);
        invite.setTokenHash(hash(rawToken));
        invite.setExpiresAt(LocalDateTime.now().plusHours(INVITE_VALID_HOURS));
        loginRepository.findByEmail(adminEmail).ifPresent(invite::setInvitedBy);
        StaffInvite saved = inviteRepository.save(invite);

        String link = frontendUrl + "/accept-invite?token=" + rawToken;
        try {
            emailService.sendStaffInvite(normalizedEmail, role.name(), link, INVITE_VALID_HOURS);
        } catch (RuntimeException e) {
            // Roll the invite back: an invite nobody can receive is useless and confusing.
            throw new IllegalStateException("The invitation email could not be sent. Check the mail configuration.", e);
        }

        audit(adminEmail, "STAFF_INVITE_CREATED", "Invited " + normalizedEmail + " as " + role);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffInviteDTO> listPending() {
        return inviteRepository.findByUsedFalseAndRevokedFalseOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revoke(Long inviteId, String adminEmail) {
        StaffInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("404: Invite not found"));
        if (invite.isUsed()) {
            throw new IllegalStateException("This invite has already been used.");
        }
        invite.setRevoked(true);
        inviteRepository.save(invite);
        audit(adminEmail, "STAFF_INVITE_REVOKED", "Revoked invite for " + invite.getEmail());
    }

    /** Looks up a usable invite for the public "validate link" call. */
    @Transactional(readOnly = true)
    public StaffInvite requireUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(GENERIC_INVALID);
        }
        StaffInvite invite = inviteRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_INVALID));
        if (!invite.isUsable(LocalDateTime.now())) {
            throw new IllegalArgumentException(GENERIC_INVALID);
        }
        return invite;
    }

    /**
     * Completes an invite: creates the login (and doctor profile) for the
     * invited email with the invited role, and burns the invite.
     */
    @Transactional
    public Login accept(AcceptInviteRequest request) {
        StaffInvite invite = requireUsable(request.getToken());

        if (loginRepository.existsByEmail(invite.getEmail())) {
            throw new IllegalStateException("An account with this email already exists.");
        }
        if (invite.getRole() == Role.DOCTOR
                && (request.getSpecialty() == null || request.getSpecialty().isBlank())) {
            throw new IllegalArgumentException("Specialty is required for doctors.");
        }

        authService.validatePasswordStrength(request.getPassword());

        Login user = new Login();
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(invite.getRole());
        if (invite.getRole() == Role.DOCTOR || invite.getRole() == Role.ADMIN) {
            user.setTwoFactorEnabled(true);
        }
        Login saved = loginRepository.save(user);

        if (invite.getRole() == Role.DOCTOR) {
            String fullName = request.getFullName().trim();
            int space = fullName.indexOf(' ');
            DoctorProfile profile = new DoctorProfile();
            profile.setUser(saved);
            profile.setFirstName(space > 0 ? fullName.substring(0, space) : fullName);
            profile.setLastName(space > 0 ? fullName.substring(space + 1).trim() : "-");
            profile.setSpecialty(request.getSpecialty().trim());
            profile.setContactNumber(request.getContactNumber());
            profile.setWorkingDays(List.of(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
            doctorProfileRepository.save(profile);
        }

        invite.setUsed(true);
        inviteRepository.save(invite);

        audit(saved.getEmail(), "STAFF_INVITE_ACCEPTED", "Account created with role " + invite.getRole());
        return saved;
    }

    private StaffInviteDTO toDTO(StaffInvite invite) {
        StaffInviteDTO dto = new StaffInviteDTO();
        dto.setId(invite.getId());
        dto.setEmail(invite.getEmail());
        dto.setRole(invite.getRole().name());
        dto.setExpiresAt(invite.getExpiresAt());
        dto.setCreatedAt(invite.getCreatedAt());
        dto.setInvitedBy(invite.getInvitedBy() != null ? invite.getInvitedBy().getEmail() : null);
        String status;
        if (invite.isUsed()) status = "USED";
        else if (invite.isRevoked()) status = "REVOKED";
        else if (!invite.getExpiresAt().isAfter(LocalDateTime.now())) status = "EXPIRED";
        else status = "PENDING";
        dto.setStatus(status);
        return dto;
    }

    private void audit(String email, String action, String details) {
        try {
            auditLogRepository.save(new AuditLog(email, action, "INTERNAL", "SYSTEM", details));
        } catch (RuntimeException ignored) {
            // Auditing must never block the operation itself.
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash invite token", e);
        }
    }
}
