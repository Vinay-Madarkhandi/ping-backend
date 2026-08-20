package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.PasswordResetToken;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.PasswordResetTokenRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.notification.DefaultNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Handles password reset tokens for the forgot-password flow.
 *
 * <p>Security model:
 * <ul>
 *   <li>Tokens are random UUIDs stored hashed (SHA-256), so a database leak does not grant reset capability.
 *   <li>Tokens expire after 1 hour.
 *   <li>Tokens are single-use and deleted after consumption.
 *   <li>All tokens for a user are invalidated after successful reset.
 *   <li>Non-existent emails return success to prevent enumeration.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DefaultNotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${frontend.url}")
    private String frontendUrl;

    /**
     * Initiate password reset by sending a reset email. Always returns success, even for
     * non-existent emails, to prevent email enumeration attacks.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Email does not exist. Return success to prevent enumeration, but do nothing.
            log.debug("Password reset requested for non-existent email: {}", email);
            return;
        }

        // Generate a random token
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        // Save hashed token
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build();
        tokenRepository.save(token);

        // Send reset email
        String resetLink = buildResetLink(rawToken);
        notificationService.sendPasswordResetEmail(user.getEmail(), user.getUserName(), resetLink);

        log.info("Password reset token generated for user: {}", user.getEmail());
    }

    /**
     * Complete password reset using the token from the email. Validates the token, updates the
     * password, and invalidates all tokens for the user.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        User user = token.getUser();

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate all reset tokens for this user
        tokenRepository.deleteByUserId(user.getId());

        log.info("Password reset completed for user: {}", user.getEmail());
    }

    /**
     * Hash a token using SHA-256. The raw token is never stored in the database.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Build the password reset link. In production, this would use the frontend URL from config.
     */
    private String buildResetLink(String token) {
        return frontendUrl + "/reset-password?token=" + token;
    }

    /**
     * Cleanup expired tokens. Should be called periodically by a scheduled job.
     */
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired password reset tokens", deleted);
        }
        return deleted;
    }
}
