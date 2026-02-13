package com.heartbeat.ping.dto.monitor;


import lombok.*;

@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CreateMonitorRequestDto {
    private String name;

    private String url;

    private int intervalMilliseconds;

    private int timeoutMilliseconds;

    private String monitorMethod;
}
