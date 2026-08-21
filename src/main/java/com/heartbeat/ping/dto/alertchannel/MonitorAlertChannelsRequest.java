package com.heartbeat.ping.dto.alertchannel;

import lombok.*;

import java.util.List;
import java.util.UUID;

/** Replaces the full set of alert channels a monitor notifies (besides the owner's email). */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonitorAlertChannelsRequest {
    private List<UUID> channelIds;
}
