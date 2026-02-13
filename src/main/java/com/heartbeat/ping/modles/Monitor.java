package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

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

    @Column(nullable = false)
    private LocalDateTime nextCheckAt;

    @Enumerated(value = EnumType.STRING)
    private MonitorMethod monitorMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "monitor" , cascade = {CascadeType.ALL}, orphanRemoval = true)
    private List<MonitorLogs> monitorLogs;
}
