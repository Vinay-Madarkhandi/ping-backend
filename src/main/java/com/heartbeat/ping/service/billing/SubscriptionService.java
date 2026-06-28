package com.heartbeat.ping.service.billing;

import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.SubscriptionStatus;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.PlanService;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Downgrades users whose paid subscription has lapsed back to FREE. Entitlement reverts automatically. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String FREE_PLAN = "FREE";

    private final UserRepository userRepository;
    private final PlanService planService;
    private final DatabaseClock clock;

    @Transactional
    public int downgradeExpired() {
        Plan free = planService.getByName(FREE_PLAN);
        int downgraded = userRepository.downgradeExpired(
                free, SubscriptionStatus.EXPIRED, SubscriptionStatus.ACTIVE, clock.now());
        if (downgraded > 0) {
            log.info("Subscription expiry: downgraded {} user(s) to FREE", downgraded);
        }
        return downgraded;
    }
}
