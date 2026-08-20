package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.StatusPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatusPageRepository extends JpaRepository<StatusPage, UUID> {

    List<StatusPage> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<StatusPage> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<StatusPage> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    long countByUser_Id(UUID userId);
}
