package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.admin.AdminUsageDto;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.UserAlertUsage;
import com.heartbeat.ping.modles.UserUsage;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.UserAlertUsageRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Builds the per-user usage snapshot served by the admin endpoint. */
@Service
@RequiredArgsConstructor
public class AdminUsageService {

    private final UserRepository userRepository;
    private final MonitorRepository monitorRepository;
    private final UserUsageRepository userUsageRepository;
    private final UserAlertUsageRepository userAlertUsageRepository;
    private final PlanService planService;
    private final DatabaseClock clock;

    @Transactional(readOnly = true)
    public List<AdminUsageDto> usage() {
        Instant now = clock.now();
        LocalDate period = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);

        return userRepository.findAll().stream().map(user -> {
            Plan plan = user.getPlan() != null ? planService.getById(user.getPlan().getId()) : null;
            long monitors = monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId());
            long checks = userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), period)
                    .map(UserUsage::getChecksConsumed).orElse(0L);
            int alerts = userAlertUsageRepository.findByUserIdAndDay(user.getId(), day)
                    .map(UserAlertUsage::getAlertsSent).orElse(0);
            boolean overQuota = plan != null && checks >= plan.getMonthlyCheckQuota();

            return new AdminUsageDto(
                    user.getId(),
                    user.getEmail(),
                    plan != null ? plan.getName() : null,
                    monitors,
                    checks,
                    alerts,
                    overQuota);
        }).toList();
    }
}
