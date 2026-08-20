package com.heartbeat.ping.controller;

import com.heartbeat.ping.config.properties.UptimeProperties;
import com.heartbeat.ping.dto.alerts.AlertResponse;
import com.heartbeat.ping.dto.analytics.IncidentResponse;
import com.heartbeat.ping.dto.analytics.MonitorLogResponseDto;
import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.dto.analytics.UptimeResponse;
import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.dto.monitor.EditMonitorRequest;
import com.heartbeat.ping.dto.monitor.ManualCheckResponse;
import com.heartbeat.ping.dto.monitor.MonitorListResponseDto;
import com.heartbeat.ping.dto.monitor.UpdateMonitorRequest;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.MonitorAnalyticService;
import com.heartbeat.ping.service.MonitorService;
import com.heartbeat.ping.service.ResourceNotFoundException;
import com.heartbeat.ping.service.execution.ManualCheckService;
import com.heartbeat.ping.service.export.CsvWriter;
import com.heartbeat.ping.service.notification.AlertHistoryService;
import com.heartbeat.ping.service.uptime.UptimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitors")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;
    private final MonitorAnalyticService monitorAnalyticService;
    private final ManualCheckService manualCheckService;
    private final AlertHistoryService alertHistoryService;
    private final UptimeService uptimeService;
    private final UptimeProperties uptimeProperties;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<CreateMonitorResponseDto> createMonitor(@Valid @RequestBody CreateMonitorRequestDto createMonitorRequestDto){
        CreateMonitorResponseDto responseDto = monitorService.monitorUrl(createMonitorRequestDto);

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<MonitorListResponseDto>> getAllMonitors(
            @RequestParam(defaultValue = "false") boolean archived,
            Authentication authentication
    ) {
        UUID userId = currentUserId(authentication);

        List<MonitorListResponseDto> monitors = archived
                ? monitorService.getArchivedMonitorsByUserId(userId)
                : monitorService.getAllMonitorsByUserId(userId);

        return new ResponseEntity<>(monitors, HttpStatus.OK);
    }

    /** CSV of all active monitors and their current summary state. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMonitors(Authentication authentication) {
        List<MonitorListResponseDto> monitors =
                monitorService.getAllMonitorsByUserId(currentUserId(authentication));

        List<String> headers = List.of(
                "Name", "URL", "Method", "Active", "State", "Uptime %", "Tags", "Created At"
        );
        List<List<String>> rows = monitors.stream()
                .map(m -> List.of(
                        m.getName(),
                        m.getUrl(),
                        m.getMethod() != null ? m.getMethod().name() : "",
                        String.valueOf(m.isActive()),
                        m.getDisplayState(),
                        String.format("%.2f", m.getUptimePercentage()),
                        m.getTags() == null ? "" : String.join(";", m.getTags()),
                        m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
                ))
                .toList();

        return csvResponse("monitors.csv", CsvWriter.write(headers, rows));
    }

    /** Full replacement of a monitor's configuration. Re-validates SSRF and plan limits. */
    @PutMapping("/{monitorId}")
    public ResponseEntity<MonitorListResponseDto> editMonitor(
            @PathVariable UUID monitorId,
            @Valid @RequestBody EditMonitorRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                monitorService.editMonitor(monitorId, currentUserId(authentication), request));
    }

    /** Runs the check immediately and returns its outcome, instead of waiting for the next poll. */
    @PostMapping("/{monitorId}/check-now")
    public ResponseEntity<ManualCheckResponse> checkNow(
            @PathVariable UUID monitorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                manualCheckService.checkNow(monitorId, currentUserId(authentication)));
    }

    /** Un-archives a monitor and schedules it immediately. Counts against the plan's monitor limit. */
    @PostMapping("/{monitorId}/restore")
    public ResponseEntity<MonitorListResponseDto> restoreMonitor(
            @PathVariable UUID monitorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                monitorService.restoreMonitor(monitorId, currentUserId(authentication)));
    }

    /** Alert delivery history for one monitor — including failed and still-pending sends. */
    @GetMapping("/{monitorId}/alerts")
    public ResponseEntity<Page<AlertResponse>> getMonitorAlerts(
            @PathVariable UUID monitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(alertHistoryService.forMonitor(
                monitorId, currentUserId(authentication), PageRequest.of(page, size)));
    }

    @GetMapping("/{monitorId}")
    public ResponseEntity<MonitorListResponseDto> getMonitor(
            @PathVariable UUID monitorId,
            Authentication authentication
    ) {
        MonitorListResponseDto dto = monitorService.getMonitorById(monitorId, currentUserId(authentication));
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{monitorId}")
    public ResponseEntity<Void> deleteMonitor(
            @PathVariable UUID monitorId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();

        monitorService.deleteMonitor(monitorId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{monitorId}/toggle")
    public ResponseEntity<Void> updateMonitor(
            @PathVariable UUID monitorId,
            @RequestBody UpdateMonitorRequest updateRequest,
            Authentication authentication
    ) {
        String email = authentication.getName();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();

        monitorService.updateMonitor(monitorId, updateRequest, userId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{monitorId}/logs")
    public ResponseEntity<Page<MonitorLogResponseDto>> getLogs(
            @PathVariable UUID monitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
        ) {
        String email = authentication.getName();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();

        Page<MonitorLogs> logs = monitorAnalyticService.getMonitorLogs(
                monitorId,
                userId,
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(
                logs.map(MonitorLogResponseDto::from)
        );
    }

    /** CSV of raw check logs in [from, to). Defaults to the last 7 days; range is capped at 90 days. */
    @GetMapping("/{monitorId}/logs/export")
    public ResponseEntity<byte[]> exportLogs(
            @PathVariable UUID monitorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Authentication authentication
    ) {
        LocalDateTime rangeEnd = to != null ? to : LocalDateTime.now();
        LocalDateTime rangeStart = from != null ? from : rangeEnd.minusDays(7);

        List<MonitorLogs> logs = monitorAnalyticService.getMonitorLogsForExport(
                monitorId, currentUserId(authentication), rangeStart, rangeEnd);

        List<String> headers = List.of(
                "Checked At", "Status Code", "Response Time (ms)", "Up", "Outcome", "Error"
        );
        List<List<String>> rows = logs.stream()
                .map(log -> List.of(
                        log.getCheckedAt() != null ? log.getCheckedAt().toString() : "",
                        String.valueOf(log.getStatusCode()),
                        String.valueOf(log.getResponseTimeInMilli()),
                        String.valueOf(log.isUp()),
                        log.getOutcome() != null ? log.getOutcome().name() : "",
                        log.getErrorMessage() != null ? log.getErrorMessage() : ""
                ))
                .toList();

        return csvResponse("monitor-logs.csv", CsvWriter.write(headers, rows));
    }

    @GetMapping("/{monitorId}/status")
    public ResponseEntity<MonitorStatusResponse> getStatus(
            @PathVariable UUID monitorId,
            Authentication authentication
    ){

        String email = authentication.getName();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();

        MonitorStatusResponse statusResponse = monitorAnalyticService.getMonitorStatus(monitorId, userId);

        return new ResponseEntity<>(statusResponse, HttpStatus.OK);
    }

    @PostMapping("/{monitorId}/pause")
    public ResponseEntity<Void> pauseMonitor(@PathVariable UUID monitorId, Authentication authentication) {
        monitorService.pauseMonitor(monitorId, currentUserId(authentication));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{monitorId}/resume")
    public ResponseEntity<Void> resumeMonitor(@PathVariable UUID monitorId, Authentication authentication) {
        monitorService.resumeMonitor(monitorId, currentUserId(authentication));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{monitorId}/incidents")
    public ResponseEntity<Page<IncidentResponse>> getIncidents(
            @PathVariable UUID monitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Page<IncidentResponse> incidents = monitorAnalyticService
                .getIncidents(monitorId, currentUserId(authentication), PageRequest.of(page, size))
                .map(IncidentResponse::from);
        return ResponseEntity.ok(incidents);
    }

    /** CSV of this monitor's incident history, most recent first (capped at 5000 rows). */
    @GetMapping("/{monitorId}/incidents/export")
    public ResponseEntity<byte[]> exportIncidents(
            @PathVariable UUID monitorId,
            Authentication authentication
    ) {
        List<Incident> incidents = monitorAnalyticService
                .getIncidentsForExport(monitorId, currentUserId(authentication));

        List<String> headers = List.of(
                "Started At", "Resolved At", "Duration (s)", "Status", "Failure Reason"
        );
        List<List<String>> rows = incidents.stream()
                .map(incident -> List.of(
                        incident.getStartedAt() != null ? incident.getStartedAt().toString() : "",
                        incident.getResolvedAt() != null ? incident.getResolvedAt().toString() : "",
                        incident.getDurationSeconds() != null ? incident.getDurationSeconds().toString() : "",
                        incident.getStatus() != null ? incident.getStatus().name() : "",
                        incident.getFailureReason() != null ? incident.getFailureReason() : ""
                ))
                .toList();

        return csvResponse("monitor-incidents.csv", CsvWriter.write(headers, rows));
    }

    @GetMapping("/{monitorId}/uptime")
    public ResponseEntity<UptimeResponse> getUptime(
            @PathVariable UUID monitorId,
            @RequestParam(required = false) String window,
            Authentication authentication
    ) {
        Duration windowDuration = (window == null || window.isBlank())
                ? uptimeProperties.getDefaultWindow()
                : DurationStyle.detectAndParse(window);
        UptimeResponse response = UptimeResponse.from(monitorId,
                uptimeService.uptime(monitorId, currentUserId(authentication), windowDuration));
        return ResponseEntity.ok(response);
    }

    private UUID currentUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(csv.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }
}
