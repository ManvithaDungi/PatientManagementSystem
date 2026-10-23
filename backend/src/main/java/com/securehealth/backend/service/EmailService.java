package com.securehealth.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service

/**
 * Service responsible for email-based communication.
 *
 * <p>This service handles sending One-Time Password (OTP) emails used
 * during Two-Factor Authentication (2FA) in the Secure Health system.</p>
 *
 * <p>It uses Spring Boot's {@link JavaMailSender} to construct and send
 * plain text email messages to users during login verification.</p>
 *
 */
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends a One-Time Password (OTP) email to a user.
     *
     * <p>This method constructs a simple text email containing the OTP
     * required for completing 2FA login and sends it to the provided
     * email address.</p>
     *
     * @param to  The recipient's email address.
     * @param otp The generated One-Time Password to be delivered.
     */
    public void sendOtp(String to, String otp) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("SecureHealth Login OTP");
        msg.setText("Your OTP is: " + otp);
        mailSender.send(msg);
    }

    /**
     * Sends a password reset email to a user.
     *
     * <p>This method constructs an email containing a secure link
     * for resetting the user's password. The link contains a unique
     * token that expires after 30 minutes.</p>
     *
     * @param to        The recipient's email address.
     * @param resetLink The full URL containing the reset token.
     */
    public void sendStaffInvite(String to, String role, String inviteLink, int validForHours) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("SecureHealth - You have been invited to join as " + role);
        msg.setText(
            "Hello,\n\n" +
            "You have been invited to create a SecureHealth staff account with the role: " + role + ".\n\n" +
            "Use the link below to set your password and activate your account:\n" +
            inviteLink + "\n\n" +
            "This link can be used once and expires in " + validForHours + " hours.\n\n" +
            "If you were not expecting this invitation, please ignore this email.\n\n" +
            "Best regards,\n" +
            "SecureHealth Team"
        );
        mailSender.send(msg);
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("SecureHealth - Password Reset Request");
        msg.setText(
            "Hello,\n\n" +
            "We received a request to reset your password for your SecureHealth account.\n\n" +
            "Click the link below to reset your password:\n" +
            resetLink + "\n\n" +
            "This link will expire in 30 minutes.\n\n" +
            "If you did not request a password reset, please ignore this email " +
            "or contact support if you have concerns.\n\n" +
            "Best regards,\n" +
            "SecureHealth Team"
        );
        mailSender.send(msg);
    }
}
