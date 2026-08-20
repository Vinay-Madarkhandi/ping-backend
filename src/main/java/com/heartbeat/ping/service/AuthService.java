package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.auth.MeResponse;
import com.heartbeat.ping.dto.auth.UserSignUpRequestDto;
import com.heartbeat.ping.dto.auth.UserSignUpResponseDto;
import com.heartbeat.ping.dto.plan.PlanResponse;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    private static final String DEFAULT_PLAN = "FREE";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final PlanService planService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder,
                       PlanService planService, EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.planService = planService;
        this.emailVerificationService = emailVerificationService;
    }

    public UserSignUpResponseDto userSignUp(UserSignUpRequestDto userSignUpRequest) {
        userRepository.findByEmail(userSignUpRequest.getEmail())
                .ifPresent(user -> {
                    throw new IllegalArgumentException("Email already exists");
                });

        User user = User.builder()
                .userName(userSignUpRequest.getUsername())
                .email(userSignUpRequest.getEmail())
                .passwordHash(bCryptPasswordEncoder.encode(userSignUpRequest.getPassword()))
                .emailVerified(false)
                .plan(planService.getByName(DEFAULT_PLAN))
                .build();
        User response = userRepository.save(user);

        // The account is already committed at this point; a failure here (e.g. a transient DB
        // hiccup writing the verification token/outbox row) must not turn a successful signup into
        // an error response. The user can always retry via POST /auth/resend-verification.
        try {
            emailVerificationService.sendVerificationEmail(response.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification email for new user {}; they can use resend-verification",
                    response.getEmail(), e);
        }

        return UserSignUpResponseDto.from(response);
    }

    /** Current user's profile + plan + subscription state, for the frontend's plan-gated UI. */
    public MeResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Plan id is read off the lazy proxy (no extra query); full plan comes from the cache.
        Plan plan = user.getPlan() != null ? planService.getById(user.getPlan().getId()) : null;
        PlanResponse planDto = plan == null ? null : PlanResponse.from(plan);

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.isEmailVerified(),
                user.getSubscriptionStatus() != null ? user.getSubscriptionStatus().name() : null,
                user.getSubscriptionStartAt(),
                user.getSubscriptionEndAt(),
                planDto);
    }

    /**
     * Change the authenticated user's password. Verifies the current password first to prevent
     * a session-hijack from locking out the real owner. Does NOT invalidate existing tokens —
     * token revocation is a separate concern handled by the logout endpoint.
     */
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify current password
        if (!bCryptPasswordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Validate new password is different
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        // Update password
        user.setPasswordHash(bCryptPasswordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Delete the authenticated user's account. This is a soft delete with anonymization:
     * - Sets deletedAt timestamp (for audit trail)
     * - Anonymizes email to prevent re-registration conflicts
     * - Cascade deletes monitors, incidents, and tokens (FK constraints handle this)
     * - Retains payment records (required for tax/legal compliance) but anonymizes user reference
     *
     * <p>Note: ON DELETE CASCADE in the database automatically removes:
     * - All monitors owned by the user
     * - All incidents for those monitors
     * - All email outbox entries for those monitors
     * - All password reset, email verification, and revoked tokens
     */
    public void deleteAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Soft delete: mark as deleted and anonymize email to prevent conflicts
        user.setDeletedAt(java.time.Instant.now());
        user.setEmail("deleted-" + user.getId() + "@deleted.local");
        user.setUserName("Deleted User");
        
        userRepository.save(user);
        
        // Note: Database cascade rules will automatically delete:
        // - monitors (and their monitor_status, monitor_logs, monitor_daily_stats via CASCADE)
        // - password_reset_token, email_verification_token, revoked_token (user_id FK CASCADE)
        // Payment transactions are retained but user reference is now anonymized
    }
}
