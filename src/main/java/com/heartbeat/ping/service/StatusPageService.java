package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.statuspage.PublicMonitorStatus;
import com.heartbeat.ping.dto.statuspage.PublicStatusPageResponse;
import com.heartbeat.ping.dto.statuspage.StatusPageMonitorSummary;
import com.heartbeat.ping.dto.statuspage.StatusPageRequest;
import com.heartbeat.ping.dto.statuspage.StatusPageResponse;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.modles.StatusPage;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.repository.StatusPageRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.uptime.UptimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Owns both the authenticated CRUD for a user's status pages and the public, unauthenticated
 * projection served at their slug. The two are kept in one service deliberately: the public
 * projection (see {@link #getPublic}) is the one place that must never leak more than a monitor's
 * display name, health state, and uptime percentage — no URL, no check config, no owner identity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusPageService {

    private static final int MAX_STATUS_PAGES_PER_USER = 3;
    private static final int MAX_MONITORS_PER_PAGE = 25;
    private static final Duration PUBLIC_UPTIME_WINDOW = Duration.ofDays(90);

    private final StatusPageRepository statusPageRepository;
    private final MonitorRepository monitorRepository;
    private final MonitorStatusRepository monitorStatusRepository;
    private final UserRepository userRepository;
    private final UptimeService uptimeService;

    @Transactional
    public StatusPageResponse create(String email, StatusPageRequest request) {
        User user = requireUser(email);

        if (statusPageRepository.countByUser_Id(user.getId()) >= MAX_STATUS_PAGES_PER_USER) {
            throw new IllegalArgumentException(
                    "You can create up to " + MAX_STATUS_PAGES_PER_USER + " status pages");
        }
        if (statusPageRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("That URL is already taken");
        }

        StatusPage page = StatusPage.builder()
                .user(user)
                .slug(request.getSlug())
                .title(request.getTitle())
                .description(request.getDescription())
                .monitors(resolveOwnedMonitors(user.getId(), request.getMonitorIds()))
                .build();

        return toResponse(statusPageRepository.save(page));
    }

    @Transactional
    public StatusPageResponse update(String email, UUID pageId, StatusPageRequest request) {
        User user = requireUser(email);
        StatusPage page = ownedPage(user.getId(), pageId);

        if (!page.getSlug().equals(request.getSlug())
                && statusPageRepository.existsBySlugAndIdNot(request.getSlug(), pageId)) {
            throw new IllegalArgumentException("That URL is already taken");
        }

        page.setSlug(request.getSlug());
        page.setTitle(request.getTitle());
        page.setDescription(request.getDescription());
        page.setMonitors(resolveOwnedMonitors(user.getId(), request.getMonitorIds()));

        return toResponse(page);
    }

    @Transactional
    public void delete(String email, UUID pageId) {
        User user = requireUser(email);
        statusPageRepository.delete(ownedPage(user.getId(), pageId));
    }

    @Transactional(readOnly = true)
    public List<StatusPageResponse> list(String email) {
        User user = requireUser(email);
        return statusPageRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatusPageResponse get(String email, UUID pageId) {
        User user = requireUser(email);
        return toResponse(ownedPage(user.getId(), pageId));
    }

    /**
     * Public, unauthenticated read. Archived monitors are silently dropped rather than erroring,
     * so deleting a monitor never breaks a published page.
     */
    @Transactional(readOnly = true)
    public PublicStatusPageResponse getPublic(String slug) {
        StatusPage page = statusPageRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Status page not found"));

        List<PublicMonitorStatus> monitors = page.getMonitors().stream()
                .filter(m -> m.getDeletedAt() == null)
                .sorted(Comparator.comparing(Monitor::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toPublicMonitorStatus)
                .toList();

        return new PublicStatusPageResponse(
                page.getTitle(), page.getDescription(), overallStatus(monitors), monitors, Instant.now());
    }

    // ---- Private helpers ----

    private PublicMonitorStatus toPublicMonitorStatus(Monitor monitor) {
        String state;
        if (monitor.isPaused()) {
            state = "PAUSED";
        } else {
            MonitorStatus status = monitorStatusRepository.findById(monitor.getId()).orElse(null);
            state = (status != null && status.getCurrentState() != null)
                    ? status.getCurrentState().name()
                    : MonitorState.UNKNOWN.name();
        }

        Double uptime = null;
        try {
            uptime = uptimeService.uptimeFor(monitor, PUBLIC_UPTIME_WINDOW).uptimePercentage();
        } catch (Exception e) {
            // Best-effort: the page still renders current state without a 90-day number rather
            // than failing the whole page over one monitor's uptime computation.
            log.warn("Failed to compute public uptime for monitor {}", monitor.getId(), e);
        }

        return new PublicMonitorStatus(monitor.getName(), state, uptime);
    }

    private String overallStatus(List<PublicMonitorStatus> monitors) {
        if (monitors.isEmpty()) {
            return "OPERATIONAL";
        }
        long downCount = monitors.stream().filter(m -> "DOWN".equals(m.state())).count();
        boolean anySuspect = monitors.stream().anyMatch(m -> "SUSPECT".equals(m.state()));

        if (downCount > monitors.size() / 2) {
            return "MAJOR_OUTAGE";
        }
        if (downCount > 0) {
            return "PARTIAL_OUTAGE";
        }
        if (anySuspect) {
            return "DEGRADED";
        }
        return "OPERATIONAL";
    }

    private List<Monitor> resolveOwnedMonitors(UUID userId, List<UUID> monitorIds) {
        if (monitorIds.size() > MAX_MONITORS_PER_PAGE) {
            throw new IllegalArgumentException(
                    "A status page can include up to " + MAX_MONITORS_PER_PAGE + " monitors");
        }
        List<Monitor> monitors = monitorRepository.findAllById(monitorIds);
        boolean allOwnedAndActive = monitors.size() == monitorIds.size()
                && monitors.stream().allMatch(m -> m.getUser().getId().equals(userId) && m.getDeletedAt() == null);
        if (!allOwnedAndActive) {
            throw new AccessDeniedException("One or more monitors were not found for this user");
        }
        return monitors;
    }

    private StatusPage ownedPage(UUID userId, UUID pageId) {
        return statusPageRepository.findByIdAndUser_Id(pageId, userId)
                .orElseThrow(() -> new AccessDeniedException("Status page not found for this user"));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private StatusPageResponse toResponse(StatusPage page) {
        List<StatusPageMonitorSummary> summaries = page.getMonitors().stream()
                .map(m -> new StatusPageMonitorSummary(m.getId(), m.getName()))
                .toList();
        return new StatusPageResponse(page.getId(), page.getSlug(), page.getTitle(), page.getDescription(), summaries);
    }
}
