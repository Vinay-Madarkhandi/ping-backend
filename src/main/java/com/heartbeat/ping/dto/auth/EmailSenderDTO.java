package com.heartbeat.ping.dto.auth;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSenderDTO {
    private String to;
    private String subject;
    private String message;
}
