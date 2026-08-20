package com.heartbeat.ping.dto.maintenance;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceWindowRequest {

    @Size(max = 200)
    private String title;

    @NotNull
    @FutureOrPresent
    private Instant startsAt;

    @NotNull
    private Instant endsAt;
}
