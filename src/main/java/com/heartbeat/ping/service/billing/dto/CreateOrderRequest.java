package com.heartbeat.ping.service.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /** Plan the user wants to move to, e.g. "PRO". */
    @NotBlank
    @JsonProperty("target_plan")
    private String targetPlan;
}
