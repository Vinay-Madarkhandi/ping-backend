package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    /**
     * Check if a token has been revoked (used by the auth filter on every request).
     */
    boolean existsByJti(String jti);

    /**
     * Delete expired revoked tokens. These are tokens whose natural expiry has passed, so
     * keeping the revocation record serves no purpose. Run periodically to prevent unbounded growth.
     */
    @Modifying
    @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(Instant now);
}
