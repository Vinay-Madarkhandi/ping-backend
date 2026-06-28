package com.heartbeat.ping.modles;

/** A user's subscription state. FREE users have never had (or no longer have) a paid plan. */
public enum SubscriptionStatus {
    FREE,
    ACTIVE,
    EXPIRED
}
