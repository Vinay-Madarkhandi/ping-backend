package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.auth.MeResponse;
import com.heartbeat.ping.dto.auth.UserSignUpRequestDto;
import com.heartbeat.ping.dto.auth.UserSignUpResponseDto;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DEFAULT_PLAN = "FREE";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final PlanService planService;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder,
                       PlanService planService) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.planService = planService;
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
                .plan(planService.getByName(DEFAULT_PLAN))
                .build();
        User response = userRepository.save(user);

        return UserSignUpResponseDto.from(response);
    }

    /** Current user's profile + plan + subscription state, for the frontend's plan-gated UI. */
    public MeResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Plan id is read off the lazy proxy (no extra query); full plan comes from the cache.
        Plan plan = user.getPlan() != null ? planService.getById(user.getPlan().getId()) : null;
        MeResponse.PlanDto planDto = plan == null ? null : new MeResponse.PlanDto(
                plan.getName(),
                plan.getMaxMonitors(),
                plan.getMinIntervalMs(),
                plan.getMaxTimeoutMs(),
                plan.getMonthlyCheckQuota(),
                plan.getRetentionDays(),
                plan.getAlertCooldownSeconds(),
                plan.getMaxAlertsPerDay(),
                plan.getPriceAmount(),
                plan.getCurrency(),
                plan.getDurationDays());

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.getSubscriptionStatus() != null ? user.getSubscriptionStatus().name() : null,
                user.getSubscriptionStartAt(),
                user.getSubscriptionEndAt(),
                planDto);
    }
}
