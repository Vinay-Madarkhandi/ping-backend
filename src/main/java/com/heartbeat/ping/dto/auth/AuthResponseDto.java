package com.heartbeat.ping.dto.auth;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@Builder
public class AuthResponseDto {
    private boolean success;
}
