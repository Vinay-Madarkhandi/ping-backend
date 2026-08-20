package com.heartbeat.ping.dto.statuspage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class StatusPageRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotBlank(message = "Slug is required")
    @Size(min = 3, max = 64, message = "Slug must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Slug can only contain lowercase letters, numbers, and hyphens")
    private String slug;

    @NotEmpty(message = "Select at least one monitor")
    private List<UUID> monitorIds;
}
