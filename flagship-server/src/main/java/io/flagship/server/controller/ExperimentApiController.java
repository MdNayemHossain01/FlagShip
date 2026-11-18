package io.flagship.server.controller;

import io.flagship.server.analytics.ExperimentService;
import io.flagship.server.entity.ExposureEvent;
import io.flagship.server.entity.GoalEvent;
import io.flagship.server.repository.ExposureEventRepository;
import io.flagship.server.repository.GoalEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/experiments")
@CrossOrigin(origins = "*")
public class ExperimentApiController {

    private final ExperimentService experimentService;
    private final ExposureEventRepository exposureRepo;
    private final GoalEventRepository goalRepo;

    public ExperimentApiController(ExperimentService experimentService,
                                   ExposureEventRepository exposureRepo,
                                   GoalEventRepository goalRepo) {
        this.experimentService = experimentService;
        this.exposureRepo = exposureRepo;
        this.goalRepo = goalRepo;
    }

    @GetMapping("/{flagKey}")
    public ExperimentService.ExperimentReport getReport(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env) {
        return experimentService.generateReport(project, env, flagKey);
    }

    @PostMapping("/{flagKey}/simulate")
    public ResponseEntity<?> simulateData(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestParam(defaultValue = "100") int usersCount,
            @RequestParam(defaultValue = "0.15") double conversionRate) {

        long start = System.currentTimeMillis();
        for (int i = 1; i <= usersCount; i++) {
            String uid = "sim-user-" + start + "-" + i;
            String var = (i % 2 == 0) ? "on" : "off";
            exposureRepo.save(new ExposureEvent(project, env, flagKey, var, uid, Instant.now()));

            if (Math.random() < conversionRate) {
                goalRepo.save(new GoalEvent(project, env, flagKey, "conversion", var, uid, 20.0, Instant.now()));
            }
        }

        return ResponseEntity.ok(Map.of("status", "simulated", "usersAdded", usersCount));
    }
}
