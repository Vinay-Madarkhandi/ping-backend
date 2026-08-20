package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.modles.AlertChannelType;

import java.util.UUID;

/** Detached snapshot of an outbox row to deliver, so the HTTP POST happens outside any transaction/lock. */
public record SendableWebhook(UUID id, String targetUrl, AlertChannelType channelType, String payload) {
}
