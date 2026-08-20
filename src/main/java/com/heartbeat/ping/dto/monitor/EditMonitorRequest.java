package com.heartbeat.ping.dto.monitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.Map;
import java.util.Set;

/**
 * Full replacement of a monitor's configuration ({@code PUT /api/v1/monitors/{id}}).
 *
 * <p>Every check-affecting field is editable. Semantics are deliberately PUT (replace), not PATCH:
 * omitting {@code keyword}, {@code expectedStatusCode} or {@code customHeaders} clears them, so a
 * user can remove a keyword requirement rather than being stuck with it forever.
 *
 * <p>The URL is re-validated against the SSRF guard and the interval/timeout against the owner's
 * current plan, so an edit can never be used to bypass limits a downgrade imposed.
 */
@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EditMonitorRequest {

    @NotBlank
    private String name;

    /** Required for HTTP monitors; ignored for HEARTBEAT monitors (which have no URL). */
    private String url;

    @Positive
    private int intervalMilliseconds;

    @Positive
    private int timeoutMilliseconds;

    private String monitorMethod;

    /** HEARTBEAT only: extra time past the interval before a missed ping counts as DOWN. */
    private Integer gracePeriodMilliseconds;

    /** Expected HTTP status; null means any 2xx/3xx counts as up. */
    private Integer expectedStatusCode;

    /** Substring that must appear in the response body; null/blank removes the requirement. */
    private String keyword;

    /** Defaults to true when omitted. */
    private Boolean followRedirects;

    private Map<String, String> customHeaders;

    /** Whether the monitor should be scheduled. Defaults to leaving the current value untouched. */
    private Boolean active;

    /** Free-form labels for grouping/filtering. Replaces the existing set; null/omitted clears them. */
    private Set<String> tags;
}
