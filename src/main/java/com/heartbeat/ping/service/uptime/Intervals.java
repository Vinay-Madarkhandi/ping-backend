package com.heartbeat.ping.service.uptime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure interval algebra (clip / merge / subtract / sum) used by the duration-based uptime
 * calculation. No DB or time-source dependency, so it is fully unit-testable.
 */
final class Intervals {

    private Intervals() {
    }

    /** Clamps each interval to [windowStart, windowEnd), dropping anything that becomes empty. */
    static List<TimeInterval> clip(List<TimeInterval> intervals, Instant windowStart, Instant windowEnd) {
        List<TimeInterval> out = new ArrayList<>();
        for (TimeInterval iv : intervals) {
            Instant start = iv.start().isAfter(windowStart) ? iv.start() : windowStart;
            Instant end = iv.end().isBefore(windowEnd) ? iv.end() : windowEnd;
            if (start.isBefore(end)) {
                out.add(new TimeInterval(start, end));
            }
        }
        return out;
    }

    /** Sorts and merges overlapping/adjacent intervals into a disjoint, ordered list. */
    static List<TimeInterval> merge(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        List<TimeInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(TimeInterval::start));

        List<TimeInterval> out = new ArrayList<>();
        Instant curStart = sorted.get(0).start();
        Instant curEnd = sorted.get(0).end();
        for (int i = 1; i < sorted.size(); i++) {
            TimeInterval iv = sorted.get(i);
            if (!iv.start().isAfter(curEnd)) { // overlapping or touching
                if (iv.end().isAfter(curEnd)) {
                    curEnd = iv.end();
                }
            } else {
                out.add(new TimeInterval(curStart, curEnd));
                curStart = iv.start();
                curEnd = iv.end();
            }
        }
        out.add(new TimeInterval(curStart, curEnd));
        return out;
    }

    /** Returns {@code a \ b}. Both inputs must already be merged (disjoint, sorted). */
    static List<TimeInterval> subtract(List<TimeInterval> a, List<TimeInterval> b) {
        List<TimeInterval> out = new ArrayList<>();
        for (TimeInterval iv : a) {
            Instant cursor = iv.start();
            for (TimeInterval sub : b) {
                if (!sub.end().isAfter(cursor)) {
                    continue; // sub entirely before the cursor
                }
                if (!sub.start().isBefore(iv.end())) {
                    break; // sub starts at/after iv end (b is sorted)
                }
                if (sub.start().isAfter(cursor)) {
                    out.add(new TimeInterval(cursor, sub.start()));
                }
                if (sub.end().isAfter(cursor)) {
                    cursor = sub.end();
                }
                if (!cursor.isBefore(iv.end())) {
                    break;
                }
            }
            if (cursor.isBefore(iv.end())) {
                out.add(new TimeInterval(cursor, iv.end()));
            }
        }
        return out;
    }

    /** Total seconds covered by a disjoint interval list. */
    static long totalSeconds(List<TimeInterval> intervals) {
        long seconds = 0;
        for (TimeInterval iv : intervals) {
            seconds += Duration.between(iv.start(), iv.end()).getSeconds();
        }
        return seconds;
    }
}
