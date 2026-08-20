package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A public, unauthenticated page publishing the current state and uptime history of a chosen
 * subset of the owner's monitors, addressed by a globally-unique {@code slug}. See
 * {@code StatusPageService} for the public-safe projection — this entity itself is never returned
 * directly to the public endpoint.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "status_page")
public class StatusPage extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String description;

    @ManyToMany
    @JoinTable(
            name = "status_page_monitor",
            joinColumns = @JoinColumn(name = "status_page_id"),
            inverseJoinColumns = @JoinColumn(name = "monitor_id")
    )
    @Builder.Default
    private List<Monitor> monitors = new ArrayList<>();
}
