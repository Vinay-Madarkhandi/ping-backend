package com.heartbeat.ping.dto.auth;

import com.heartbeat.ping.modles.User;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpResponseDto {
    private UUID id;

    private String username;

    public static UserSignUpResponseDto from(User user){
        return UserSignUpResponseDto.builder()
                .username(user.getUserName())
                .id(user.getId())
                .build();
    }
}
