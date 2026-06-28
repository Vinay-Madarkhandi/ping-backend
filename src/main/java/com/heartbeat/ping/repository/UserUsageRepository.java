package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.UserUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserUsageRepository extends JpaRepository<UserUsage, UUID> {

    Optional<UserUsage> findByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    /**
     * Atomically increments the user's check count for the month, inserting the row on first use.
     * Single-statement upsert (Postgres {@code ON CONFLICT}) so concurrent checks for the same user
     * never lose an increment or fail on the unique constraint.
     */
    @Modifying
    @Query(value = """
            insert into user_usage (id, user_id, period_month, checks_consumed)
            values (gen_random_uuid(), :userId, :periodMonth, 1)
            on conflict (user_id, period_month)
            do update set checks_consumed = user_usage.checks_consumed + 1
            """, nativeQuery = true)
    void incrementChecks(@Param("userId") UUID userId, @Param("periodMonth") LocalDate periodMonth);

    /** User ids whose consumption for the period has reached or exceeded their plan's monthly quota. */
    @Query("""
            select uu.userId from UserUsage uu, User u
            where uu.userId = u.id and uu.periodMonth = :period
              and uu.checksConsumed >= u.plan.monthlyCheckQuota
            """)
    List<UUID> findOverQuotaUserIds(@Param("period") LocalDate period);
}
