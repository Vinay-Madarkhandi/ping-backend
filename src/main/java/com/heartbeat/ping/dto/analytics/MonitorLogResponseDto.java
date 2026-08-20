package com.heartbeat.ping.dto.analytics;

import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.ProbeOutcome;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorLogResponseDto {
    private int statusCode;
    private boolean isUp;
    /**
     * UP / DOWN / INCONCLUSIVE. Exposed so clients can distinguish an infrastructure failure on our
     * side (INCONCLUSIVE) from a real target outage instead of guessing from {@code statusCode == 0},
     * which also matches genuine connect/read timeouts. Null for rows written before migration V11.
     */
    private ProbeOutcome outcome;
    private int responseTimeInMilli;
    private String errorMessage;
    private LocalDateTime checkedAt;

    public static MonitorLogResponseDto from(MonitorLogs log) {
        return MonitorLogResponseDto.builder()
                .statusCode(log.getStatusCode())
                .isUp(log.isUp())
                .outcome(log.getOutcome())
                .responseTimeInMilli(log.getResponseTimeInMilli())
                .errorMessage(log.getErrorMessage())
                .checkedAt(log.getCheckedAt())
                .build();
    }
}
