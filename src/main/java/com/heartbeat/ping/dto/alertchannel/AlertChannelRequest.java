package com.heartbeat.ping.dto.alertchannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertChannelRequest {

    @NotBlank
    @Size(max = 16)
    private String type;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Size(max = 2048)
    private String targetUrl;
}
