package com.heartbeat.ping.dto.monitor;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.Map;
import java.util.Set;

@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CreateMonitorRequestDto {

    @NotBlank
    private String name;

    /** Required for HTTP monitors; ignored (and left unset) for HEARTBEAT monitors. */
    private String url;

    @Positive
    private int intervalMilliseconds;

    @Positive
    private int timeoutMilliseconds;

    private String monitorMethod;

    /** "HTTP" (default) or "HEARTBEAT". Immutable after creation. */
    private String kind;

    /** HEARTBEAT only: extra time past the interval before a missed ping counts as DOWN. */
    private Integer gracePeriodMilliseconds;

    // ---- Optional check configuration ----

    /** Expected HTTP status; when null any 2xx/3xx counts as up. */
    private Integer expectedStatusCode;

    /** Substring that must appear in the response body for the check to pass. */
    private String keyword;

    /** Whether to follow redirects (defaults to true when omitted). */
    private Boolean followRedirects;

    private Map<String, String> customHeaders;

    /** Free-form labels for grouping/filtering. Optional; normalized and capped by the service. */
    private Set<String> tags;
}
