package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.EmailVerificationToken;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.EmailVerificationTokenRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.notification.DefaultNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Handles email verification for new signups and resend requests.
 *
 * <p>Security model:
 * <ul>
 *   <li>Tokens are random UUIDs stored hashed (SHA-256).
 *   <li>Tokens expire after 24 hours (longer than password reset since it's lower risk).
 *   <li>Tokens are single-use and deleted after consumption.
 *   <li>Unverified users can still sign in but should be blocked from sending alerts.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final DefaultNotificationService notificationService;

    /**
     * Send or resend a verification email for a user. Called on signup or when user clicks "resend".
     */
    @Transactional
    public void sendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isEmailVerified()) {
            log.debug("Email already verified for user: {}", email);
            return; // Already verified, nothing to do
        }

        // Generate a random token
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        // Save hashed token
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build();
        tokenRepository.save(token);

        // Send verification email
        String verifyLink = buildVerificationLink(rawToken);
        notificationService.sendEmailVerificationEmail(user.getEmail(), user.getUserName(), verifyLink);

        log.info("Email verification token generated for user: {}", email);
    }

    /**
     * Verify email using the token from the verification email. Marks user as verified and
     * invalidates all verification tokens for the user.
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Invalid or expired verification token");
        }

        User user = token.getUser();

        // Mark user as verified
        user.setEmailVerified(true);
        userRepository.save(user);

        // Invalidate all verification tokens for this user
        tokenRepository.deleteByUserId(user.getId());

        log.info("Email verified for user: {}", user.getEmail());
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
     * Build the email verification link. In production, this would use the frontend URL from config.
     */
    private String buildVerificationLink(String token) {
        // TODO: Read frontend URL from application properties
        return "https://app.pingmeheart.online/verify-email?token=" + token;
    }

    /**
     * Cleanup expired tokens. Should be called periodically by a scheduled job.
     */
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired email verification tokens", deleted);
        }
        return deleted;
    }
}
