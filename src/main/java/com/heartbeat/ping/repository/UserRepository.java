package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.SubscriptionStatus;
import com.heartbeat.ping.modles.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    /** Downgrades users whose paid subscription has lapsed back to the FREE plan. Set-based. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update User u set u.plan = :free, u.subscriptionStatus = :expired
            where u.subscriptionStatus = :active and u.subscriptionEndAt < :now
            """)
    int downgradeExpired(@Param("free") Plan free,
                         @Param("expired") SubscriptionStatus expired,
                         @Param("active") SubscriptionStatus active,
                         @Param("now") Instant now);
}
