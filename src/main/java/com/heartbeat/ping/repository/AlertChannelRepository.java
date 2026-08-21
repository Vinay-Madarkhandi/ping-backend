package com.heartbeat.ping.repository;

import com.heartbeat.ping.modles.AlertChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertChannelRepository extends JpaRepository<AlertChannel, UUID> {

    List<AlertChannel> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<AlertChannel> findByIdAndUser_Id(UUID id, UUID userId);

    List<AlertChannel> findByIdInAndUser_Id(List<UUID> ids, UUID userId);
}
