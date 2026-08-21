package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.statuspage.PublicStatusPageResponse;
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
import com.heartbeat.ping.service.uptime.UptimeResult;
import com.heartbeat.ping.service.uptime.UptimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusPageServiceTest {

    @Mock private StatusPageRepository statusPageRepository;
    @Mock private MonitorRepository monitorRepository;
    @Mock private MonitorStatusRepository monitorStatusRepository;
    @Mock private UserRepository userRepository;
    @Mock private UptimeService uptimeService;

    private StatusPageService service;
    private User owner;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new StatusPageService(statusPageRepository, monitorRepository, monitorStatusRepository,
                userRepository, uptimeService, passwordEncoder, new PublicStatusPageCache());
        owner = User.builder().userName("jane").email("jane@example.com").build();
        owner.setId(UUID.randomUUID());
    }

    private Monitor monitor(String name, UUID ownerId, boolean paused, boolean archived) {
        User user = User.builder().build();
        user.setId(ownerId);
        Monitor monitor = Monitor.builder().name(name).user(user).paused(paused).build();
        monitor.setId(UUID.randomUUID());
        if (archived) {
            monitor.setDeletedAt(Instant.now());
        }
        return monitor;
    }

    private StatusPageRequest request(String title, String slug, List<UUID> monitorIds) {
        StatusPageRequest request = new StatusPageRequest();
        request.setTitle(title);
        request.setSlug(slug);
        request.setMonitorIds(monitorIds);
        return request;
    }

    @Test
    void createRejectsMonitorsNotOwnedByTheCaller() {
        Monitor someoneElses = monitor("api", UUID.randomUUID(), false, false);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(monitorRepository.findAllById(List.of(someoneElses.getId())))
                .thenReturn(List.of(someoneElses));

        assertThatThrownBy(() -> service.create(
                owner.getEmail(), request("Status", "status", List.of(someoneElses.getId()))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createRejectsArchivedMonitors() {
        Monitor archived = monitor("api", owner.getId(), false, true);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(monitorRepository.findAllById(List.of(archived.getId()))).thenReturn(List.of(archived));

        assertThatThrownBy(() -> service.create(
                owner.getEmail(), request("Status", "status", List.of(archived.getId()))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createRejectsADuplicateSlug() {
        Monitor mine = monitor("api", owner.getId(), false, false);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(statusPageRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                owner.getEmail(), request("Status", "taken", List.of(mine.getId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void updateRejectsAnotherUsersPage() {
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        UUID pageId = UUID.randomUUID();
        when(statusPageRepository.findByIdAndUser_Id(pageId, owner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(owner.getEmail(), pageId, request("x", "x", List.of())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void publicProjectionDropsArchivedMonitorsAndNeverExposesTheUrl() {
        Monitor up = monitor("api.example.com", owner.getId(), false, false);
        Monitor archived = monitor("old-service", owner.getId(), false, true);
        StatusPage page = StatusPage.builder()
                .slug("acme").title("Acme Status").description("desc")
                .monitors(List.of(up, archived))
                .build();
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(page));

        MonitorStatus status = MonitorStatus.builder().currentState(MonitorState.UP).build();
        when(monitorStatusRepository.findById(up.getId())).thenReturn(Optional.of(status));
        when(uptimeService.uptimeFor(any(Monitor.class), any()))
                .thenReturn(new UptimeResult(99.9, Instant.now(), Instant.now(), 0, 0, 0, 0, 0));

        PublicStatusPageResponse response = service.getPublic("acme");

        assertThat(response.title()).isEqualTo("Acme Status");
        assertThat(response.monitors()).hasSize(1);
        assertThat(response.monitors().get(0).name()).isEqualTo("api.example.com");
        assertThat(response.monitors().get(0).state()).isEqualTo("UP");
        assertThat(response.overallStatus()).isEqualTo("OPERATIONAL");
    }

    @Test
    void overallStatusIsMajorOutageWhenMoreThanHalfTheMonitorsAreDown() {
        Monitor down1 = monitor("a", owner.getId(), false, false);
        Monitor down2 = monitor("b", owner.getId(), false, false);
        Monitor up = monitor("c", owner.getId(), false, false);
        StatusPage page = StatusPage.builder()
                .slug("acme").title("Acme").monitors(List.of(down1, down2, up)).build();
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(page));

        when(monitorStatusRepository.findById(down1.getId()))
                .thenReturn(Optional.of(MonitorStatus.builder().currentState(MonitorState.DOWN).build()));
        when(monitorStatusRepository.findById(down2.getId()))
                .thenReturn(Optional.of(MonitorStatus.builder().currentState(MonitorState.DOWN).build()));
        when(monitorStatusRepository.findById(up.getId()))
                .thenReturn(Optional.of(MonitorStatus.builder().currentState(MonitorState.UP).build()));
        when(uptimeService.uptimeFor(any(Monitor.class), any()))
                .thenReturn(new UptimeResult(99.0, Instant.now(), Instant.now(), 0, 0, 0, 0, 0));

        PublicStatusPageResponse response = service.getPublic("acme");

        assertThat(response.overallStatus()).isEqualTo("MAJOR_OUTAGE");
    }

    @Test
    void pausedMonitorReportsPausedRegardlessOfLastKnownState() {
        Monitor paused = monitor("api", owner.getId(), true, false);
        StatusPage page = StatusPage.builder().slug("acme").title("Acme").monitors(List.of(paused)).build();
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(page));
        when(uptimeService.uptimeFor(any(Monitor.class), any()))
                .thenReturn(new UptimeResult(100.0, Instant.now(), Instant.now(), 0, 0, 0, 0, 0));

        PublicStatusPageResponse response = service.getPublic("acme");

        assertThat(response.monitors().get(0).state()).isEqualTo("PAUSED");
    }

    @Test
    void getPublicThrowsResourceNotFoundForAnUnknownSlug() {
        when(statusPageRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublic("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsALogoUrlWithoutAnHttpScheme() {
        Monitor mine = monitor("api", owner.getId(), false, false);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));

        StatusPageRequest request = request("Status", "status", List.of(mine.getId()));
        request.setLogoUrl("javascript:alert(1)");

        assertThatThrownBy(() -> service.create(owner.getEmail(), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createHashesThePasswordRatherThanStoringItInTheClear() {
        Monitor mine = monitor("api", owner.getId(), false, false);
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(monitorRepository.findAllById(List.of(mine.getId()))).thenReturn(List.of(mine));
        when(statusPageRepository.save(any(StatusPage.class))).thenAnswer(inv -> inv.getArgument(0));

        StatusPageRequest request = request("Status", "status", List.of(mine.getId()));
        request.setPassword("hunter2");

        StatusPageResponse response = service.create(owner.getEmail(), request);

        assertThat(response.passwordProtected()).isTrue();
    }

    @Test
    void updateWithNullPasswordLeavesTheExistingPasswordUnchanged() {
        StatusPage page = StatusPage.builder().slug("acme").title("Acme")
                .passwordHash(passwordEncoder.encode("original")).monitors(List.of()).build();
        page.setId(UUID.randomUUID());
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(statusPageRepository.findByIdAndUser_Id(page.getId(), owner.getId())).thenReturn(Optional.of(page));

        StatusPageRequest request = request("Acme", "acme", List.of()); // password left null

        service.update(owner.getEmail(), page.getId(), request);

        assertThat(passwordEncoder.matches("original", page.getPasswordHash())).isTrue();
    }

    @Test
    void updateWithBlankPasswordRemovesProtection() {
        StatusPage page = StatusPage.builder().slug("acme").title("Acme")
                .passwordHash(passwordEncoder.encode("original")).monitors(List.of()).build();
        page.setId(UUID.randomUUID());
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(statusPageRepository.findByIdAndUser_Id(page.getId(), owner.getId())).thenReturn(Optional.of(page));

        StatusPageRequest request = request("Acme", "acme", List.of());
        request.setPassword("");

        StatusPageResponse response = service.update(owner.getEmail(), page.getId(), request);

        assertThat(response.passwordProtected()).isFalse();
        assertThat(page.getPasswordHash()).isNull();
    }

    @Test
    void getPublicThrowsPasswordRequiredForAProtectedPage() {
        StatusPage page = StatusPage.builder().slug("acme").title("Acme")
                .passwordHash(passwordEncoder.encode("secret")).monitors(List.of()).build();
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> service.getPublic("acme"))
                .isInstanceOf(StatusPagePasswordException.class);
    }

    @Test
    void unlockPublicSucceedsWithTheCorrectPassword() {
        StatusPage page = StatusPage.builder().slug("acme").title("Acme")
                .passwordHash(passwordEncoder.encode("secret")).monitors(List.of()).build();
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(page));

        PublicStatusPageResponse response = service.unlockPublic("acme", "secret");

        assertThat(response.title()).isEqualTo("Acme");
    }

    @Test
    void unlockPublicRejectsAnIncorrectPassword() {
        StatusPage page = StatusPage.builder().slug("acme").title("Acme")
                .passwordHash(passwordEncoder.encode("secret")).monitors(List.of()).build();
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> service.unlockPublic("acme", "wrong"))
                .isInstanceOf(StatusPagePasswordException.class);
    }

    @Test
    void listReturnsOwnersPagesOnly() {
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        StatusPage page = StatusPage.builder().slug("acme").title("Acme").monitors(List.of()).build();
        page.setId(UUID.randomUUID());
        when(statusPageRepository.findByUser_IdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of(page));

        List<StatusPageResponse> pages = service.list(owner.getEmail());

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).slug()).isEqualTo("acme");
    }
}
