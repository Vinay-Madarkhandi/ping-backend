package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of the (few, rarely changing) {@link Plan} rows, so hot paths — the alert engine,
 * the quota job, monitor creation — resolve plan limits without a database join per call. Lookups
 * fall back to a reload when a key is missing, so plans edited via SQL are picked up.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    private final Map<UUID, Plan> byId = new ConcurrentHashMap<>();
    private final Map<String, Plan> byName = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public Plan getById(UUID id) {
        ensureLoaded();
        Plan plan = byId.get(id);
        if (plan == null) {
            reload();
            plan = byId.get(id);
        }
        return plan;
    }

    public Plan getByName(String name) {
        ensureLoaded();
        Plan plan = byName.get(name);
        if (plan == null) {
            reload();
            plan = byName.get(name);
        }
        return plan;
    }

    private void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    /** Reloads the cache from the database. Safe to call at any time; plans are read-only reference data. */
    public synchronized void reload() {
        Map<UUID, Plan> ids = new HashMap<>();
        Map<String, Plan> names = new HashMap<>();
        for (Plan plan : planRepository.findAll()) {
            ids.put(plan.getId(), plan);
            names.put(plan.getName(), plan);
        }
        byId.clear();
        byId.putAll(ids);
        byName.clear();
        byName.putAll(names);
        loaded = true;
    }
}
