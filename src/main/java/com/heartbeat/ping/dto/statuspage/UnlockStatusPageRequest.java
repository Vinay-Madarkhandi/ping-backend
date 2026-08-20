package com.heartbeat.ping.dto.statuspage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnlockStatusPageRequest {

    @NotBlank(message = "Password is required")
    private String password;
}
