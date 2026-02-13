package com.heartbeat.ping.dto.analytics;

import com.heartbeat.ping.modles.MonitorLogs;
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
    private int responseTimeInMilli;
    private String errorMessage;
    private LocalDateTime checkedAt;

    public static MonitorLogResponseDto from(MonitorLogs log) {
        return MonitorLogResponseDto.builder()
                .statusCode(log.getStatusCode())
                .isUp(log.isUp())
                .responseTimeInMilli(log.getResponseTimeInMilli())
                .errorMessage(log.getErrorMessage())
                .checkedAt(log.getCheckedAt())
                .build();
    }
}
