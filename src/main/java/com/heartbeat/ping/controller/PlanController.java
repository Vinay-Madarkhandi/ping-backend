package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.plan.PlanResponse;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * The plan catalog, so pricing and plan-comparison UI is driven by the same reference data the
 * backend enforces limits from — never by hardcoded numbers in the frontend that can drift.
 * Sorted cheapest-first so callers can render tiers in order without re-sorting.
 */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanRepository planRepository;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> plans() {
        List<PlanResponse> plans = planRepository.findAll().stream()
                .sorted(Comparator.comparingLong(Plan::getPriceAmount))
                .map(PlanResponse::from)
                .toList();
        return ResponseEntity.ok(plans);
    }
}
