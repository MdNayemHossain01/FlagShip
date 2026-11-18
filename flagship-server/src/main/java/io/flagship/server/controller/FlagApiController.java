package io.flagship.server.controller;

import io.flagship.core.model.FlagType;
import io.flagship.server.entity.*;
import io.flagship.server.repository.*;
import io.flagship.server.service.FlagService;
import io.flagship.server.service.ScheduledChangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/flags")
@CrossOrigin(origins = "*")
public class FlagApiController {

    private final FeatureFlagRepository flagRepository;
    private final FlagEnvironmentConfigRepository configRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository envRepository;
    private final FlagService flagService;
    private final ScheduledChangeService scheduledChangeService;

    public FlagApiController(
            FeatureFlagRepository flagRepository,
            FlagEnvironmentConfigRepository configRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository envRepository,
            FlagService flagService,
            ScheduledChangeService scheduledChangeService) {
        this.flagRepository = flagRepository;
        this.configRepository = configRepository;
        this.projectRepository = projectRepository;
        this.envRepository = envRepository;
        this.flagService = flagService;
        this.scheduledChangeService = scheduledChangeService;
    }

    @GetMapping
    public List<FeatureFlag> listFlags(@RequestParam(defaultValue = "default") String project) {
        return flagRepository.findByProjectKeyAndArchivedFalse(project);
    }

    @PostMapping
    public ResponseEntity<?> createFlag(
            @RequestParam(defaultValue = "default") String projectKey,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        String key = (String) body.get("key");
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String typeStr = (String) body.getOrDefault("type", "BOOLEAN");
        FlagType type = FlagType.valueOf(typeStr.toUpperCase());
        String owner = auth != null ? auth.getName() : "admin";

        Project project = projectRepository.findByKey(projectKey)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectKey));

        if (flagRepository.existsByProjectKeyAndKey(projectKey, key)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Flag key already exists: " + key));
        }

        FeatureFlag flag = new FeatureFlag(project, key, name, description, type, owner);
        flagRepository.save(flag);

        // Create environment configs for each environment in project
        List<Environment> envs = envRepository.findByProjectKey(projectKey);
        for (Environment env : envs) {
            FlagEnvironmentConfig cfg = new FlagEnvironmentConfig(flag, env);
            configRepository.save(cfg);
        }

        return ResponseEntity.ok(Map.of("status", "created", "flagKey", key));
    }

    @PostMapping("/{flagKey}/toggle")
    public ResponseEntity<?> toggleFlag(
            @PathVariable String flagKey,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestParam(required = false) String reason,
            Authentication auth) throws Exception {

        String author = auth != null ? auth.getName() : "system";
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean appliedImmediately = flagService.toggleKillSwitch(project, env, flagKey, enabled, author, reason, isAdmin);

        return ResponseEntity.ok(Map.of(
            "flagKey", flagKey,
            "enabled", enabled,
            "appliedImmediately", appliedImmediately,
            "status", appliedImmediately ? "APPLIED" : "PENDING_APPROVAL"
        ));
    }

    @PutMapping("/{flagKey}/targeting")
    public ResponseEntity<?> updateTargeting(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestBody Map<String, Object> body,
            Authentication auth) throws Exception {

        int rollout = ((Number) body.getOrDefault("rolloutPercentage", 0)).intValue();
        String defaultVar = (String) body.get("defaultVariation");
        String offVar = (String) body.get("offVariation");
        String targets = (String) body.get("individualTargetsJson");
        String rules = (String) body.get("rulesJson");
        String reason = (String) body.get("changeReason");

        String author = auth != null ? auth.getName() : "system";
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean appliedImmediately = flagService.updateFlagConfig(
            project, env, flagKey, rollout, defaultVar, offVar, targets, rules, author, reason, isAdmin
        );

        return ResponseEntity.ok(Map.of(
            "flagKey", flagKey,
            "appliedImmediately", appliedImmediately,
            "status", appliedImmediately ? "APPLIED" : "PENDING_APPROVAL"
        ));
    }

    @PostMapping("/{flagKey}/rollback")
    public ResponseEntity<?> rollback(
            @PathVariable String flagKey,
            @RequestParam long targetVersion,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Authentication auth) throws Exception {

        String author = auth != null ? auth.getName() : "system";
        flagService.rollbackToVersion(project, env, flagKey, targetVersion, author);

        return ResponseEntity.ok(Map.of("status", "ROLLED_BACK", "version", targetVersion));
    }

    @DeleteMapping("/{flagKey}")
    public ResponseEntity<?> deleteFlag(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "default") String project,
            Authentication auth) throws Exception {

        String author = auth != null ? auth.getName() : "system";
        flagService.archiveFlag(project, flagKey, author);

        return ResponseEntity.ok(Map.of("status", "ARCHIVED", "flagKey", flagKey));
    }

    @PostMapping("/{flagKey}/schedule")
    public ResponseEntity<?> scheduleChange(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestBody Map<String, Object> body,
            Authentication auth) throws Exception {

        String author = auth != null ? auth.getName() : "system";
        Instant time = Instant.parse((String) body.get("scheduledTime"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");

        ScheduledChange sc = scheduledChangeService.scheduleChange(project, env, flagKey, author, time, payload);

        return ResponseEntity.ok(Map.of(
            "status", "SCHEDULED",
            "scheduleId", sc.getId(),
            "scheduledTime", sc.getScheduledTime().toString(),
            "flagKey", flagKey
        ));
    }
}
