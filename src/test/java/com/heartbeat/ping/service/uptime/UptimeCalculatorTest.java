package com.heartbeat.ping.service.uptime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UptimeCalculatorTest {

    private final UptimeCalculator calculator = new UptimeCalculator();

    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    private static Instant at(long seconds) {
        return T0.plusSeconds(seconds);
    }

    private static TimeInterval iv(long startSec, long endSec) {
        return new TimeInterval(at(startSec), at(endSec));
    }

    @Test
    void downIntervalReducesUptime() {
        // 100s window, 10s down -> 90%
        UptimeResult r = calculator.compute(T0, at(100), List.of(iv(20, 30)), List.of(), List.of());

        assertThat(r.monitoredSeconds()).isEqualTo(100);
        assertThat(r.downSeconds()).isEqualTo(10);
        assertThat(r.uptimePercentage()).isCloseTo(90.0, within(1e-9));
    }

    @Test
    void pausedTimeIsExcludedFromDenominator() {
        // 100s window, paused [0,50) excluded, down [60,70) within monitored -> monitored 50, down 10 -> 80%
        UptimeResult r = calculator.compute(T0, at(100),
                List.of(iv(60, 70)), List.of(iv(0, 50)), List.of());

        assertThat(r.pausedSeconds()).isEqualTo(50);
        assertThat(r.monitoredSeconds()).isEqualTo(50);
        assertThat(r.downSeconds()).isEqualTo(10);
        assertThat(r.uptimePercentage()).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void gapTimeIsExcludedFromDenominator() {
        // 100s window, 20s gap (no data) excluded -> monitored 80, no down -> 100%
        UptimeResult r = calculator.compute(T0, at(100), List.of(), List.of(), List.of(iv(40, 60)));

        assertThat(r.gapSeconds()).isEqualTo(20);
        assertThat(r.monitoredSeconds()).isEqualTo(80);
        assertThat(r.uptimePercentage()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void downOverlappingPausedOrGapIsNotCounted() {
        // down [10,40) overlaps paused [0,30) and gap [30,50): only down has no exclusive time -> 100%
        UptimeResult r = calculator.compute(T0, at(100),
                List.of(iv(10, 40)), List.of(iv(0, 30)), List.of(iv(30, 50)));

        assertThat(r.pausedSeconds()).isEqualTo(30);
        assertThat(r.gapSeconds()).isEqualTo(20);   // [30,50)
        assertThat(r.downSeconds()).isZero();        // [10,40) fully inside paused+gap
        assertThat(r.monitoredSeconds()).isEqualTo(50);
        assertThat(r.uptimePercentage()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void noMonitoredTimeYieldsNullUptime() {
        // entire window paused -> nothing measured
        UptimeResult r = calculator.compute(T0, at(100), List.of(), List.of(iv(0, 100)), List.of());

        assertThat(r.monitoredSeconds()).isZero();
        assertThat(r.uptimePercentage()).isNull();
    }

    @Test
    void openIncidentCountedToWindowEnd() {
        // ongoing outage modelled as down up to window end: [80,100) -> down 20, monitored 100 -> 80%
        UptimeResult r = calculator.compute(T0, at(100), List.of(iv(80, 100)), List.of(), List.of());

        assertThat(r.downSeconds()).isEqualTo(20);
        assertThat(r.uptimePercentage()).isCloseTo(80.0, within(1e-9));
    }
}
