package com.heartbeat.ping.modles;

/** Kind of outbound alert channel. Determines how the outbox payload is formatted. */
public enum AlertChannelType {
    WEBHOOK,
    SLACK,
    DISCORD
}
