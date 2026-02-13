package com.heartbeat.ping.dto.auth;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpRequestDto {

    private String username;

    private String email;

    private String password;
}
