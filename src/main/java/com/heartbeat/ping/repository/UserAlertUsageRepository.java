package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.UserAlertUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAlertUsageRepository extends JpaRepository<UserAlertUsage, UUID> {

    Optional<UserAlertUsage> findByUserIdAndDay(UUID userId, LocalDate day);

    /** Atomically increments the user's alert count for the day, inserting the row on first use. */
    @Modifying
    @Query(value = """
            insert into user_alert_usage (id, user_id, usage_day, alerts_sent)
            values (gen_random_uuid(), :userId, :day, 1)
            on conflict (user_id, usage_day)
            do update set alerts_sent = user_alert_usage.alerts_sent + 1
            """, nativeQuery = true)
    void incrementAlerts(@Param("userId") UUID userId, @Param("day") LocalDate day);
}
