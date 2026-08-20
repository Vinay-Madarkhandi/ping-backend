package com.heartbeat.ping.modles;

import com.heartbeat.ping.modles.converter.JsonStringMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitor")
public class Monitor extends BaseModel{

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    private int intervalMilliseconds;

    private int timeoutMilliseconds;

    @Builder.Default
    private boolean isActive = true;

    /** When true the monitor is administratively paused and skipped by the scheduler. */
    @Builder.Default
    private boolean paused = false;

    /** Set when the monitor is archived (soft delete); such monitors are never claimed. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant deletedAt;

    /**
     * When true the owning user is over their monthly check quota, so the scheduler skips this
     * monitor (see {@code MonitorRepository.claimDueMonitors}). Maintained by the quota
     * enforcement job and cleared automatically at month rollover.
     */
    @Builder.Default
    private boolean quotaBlocked = false;

    /**
     * Next time this monitor is due. Stored as a UTC instant in a plain TIMESTAMP column
     * (via {@link JdbcTypeCode}) so due-time comparisons are timezone-independent across instances.
     * While a check is running this also acts as the lease — see {@code MonitorClaimService}.
     */
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant nextCheckAt;

    @Enumerated(value = EnumType.STRING)
    private MonitorMethod monitorMethod;

    /** Optimistic-lock guard so a leased monitor cannot be double-executed. */
    @Version
    @Builder.Default
    private long version = 0;

    // ---- Check configuration ----

    /** Expected HTTP status; when null any 2xx counts as up. */
    private Integer expectedStatusCode;

    /** Optional substring that must appear in the response body for the check to pass. */
    private String keyword;

    @Builder.Default
    private boolean followRedirects = true;

    @Convert(converter = JsonStringMapConverter.class)
    @Column(name = "custom_headers", columnDefinition = "text")
    private Map<String, String> customHeaders;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    /** Free-form labels for grouping/filtering (e.g. "prod", "api"). Not shown on public status pages. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monitor_tag", joinColumns = @JoinColumn(name = "monitor_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "monitor" , cascade = {CascadeType.ALL}, orphanRemoval = true)
    private List<MonitorLogs> monitorLogs;

    /** Additional alert destinations (webhook/Slack/Discord) this monitor notifies besides email. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "monitor_alert_channel",
            joinColumns = @JoinColumn(name = "monitor_id"),
            inverseJoinColumns = @JoinColumn(name = "channel_id"))
    @Builder.Default
    private Set<AlertChannel> alertChannels = new LinkedHashSet<>();
}
