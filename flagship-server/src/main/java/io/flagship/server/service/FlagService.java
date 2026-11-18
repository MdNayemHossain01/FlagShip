package io.flagship.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import io.flagship.server.entity.*;
import io.flagship.server.repository.*;
import io.flagship.server.sse.SseFanOutHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FlagService {
    private static final Logger log = LoggerFactory.getLogger(FlagService.class);

    private final FeatureFlagRepository flagRepository;
    private final FlagEnvironmentConfigRepository configRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final FlagVersionRepository versionRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final SseFanOutHub sseHub;
    private final ObjectMapper objectMapper;

    private final AtomicLong globalRevision = new AtomicLong(100L);

    public FlagService(
            FeatureFlagRepository flagRepository,
            FlagEnvironmentConfigRepository configRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            FlagVersionRepository versionRepository,
            ApprovalRequestRepository approvalRepository,
            SseFanOutHub sseHub,
            ObjectMapper objectMapper) {
        this.flagRepository = flagRepository;
        this.configRepository = configRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.versionRepository = versionRepository;
        this.approvalRepository = approvalRepository;
        this.sseHub = sseHub;
        this.objectMapper = objectMapper;
    }

    public long getGlobalRevision() {
        return globalRevision.get();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSnapshot(String projectKey, String envKey) {
        List<FlagEnvironmentConfig> configs = configRepository
            .findByEnvironmentProjectKeyAndEnvironmentKey(projectKey, envKey);

        Map<String, FlagDefinition> flagMap = new HashMap<>();
        long maxVersion = 1L;

        for (FlagEnvironmentConfig config : configs) {
            FeatureFlag flag = config.getFlag();
            if (flag.isArchived()) continue;
            FlagDefinition def = buildFlagDefinition(flag, config);
            flagMap.put(def.key(), def);
            if (config.getCurrentVersion() > maxVersion) {
                maxVersion = config.getCurrentVersion();
            }
        }

        long revision = globalRevision.get() + maxVersion;
        return Map.of(
            "revision", revision,
            "syncedAt", Instant.now().toString(),
            "flags", flagMap
        );
    }

    public FlagDefinition buildFlagDefinition(FeatureFlag flag, FlagEnvironmentConfig config) {
        Map<String, Object> variations = parseVariations(config.getVariationsJson(), flag.getType());
        Map<String, Set<String>> targets = parseTargets(config.getIndividualTargetsJson());
        List<FlagDefinition.SegmentRule> rules = parseRules(config.getRulesJson());

        // Construct 100,000-point rollout configuration
        int pct = Math.max(0, Math.min(100, config.getRolloutPercentage()));
        int onWeight = pct * 1000;
        int offWeight = 100_000 - onWeight;

        String onVariation = "on";
        if (!variations.containsKey("on") && !variations.isEmpty()) {
            onVariation = variations.keySet().stream().filter(k -> !k.equals(config.getOffVariation())).findFirst().orElse(config.getDefaultVariation());
        }

        List<FlagDefinition.WeightedVariation> buckets = new ArrayList<>();
        if (onWeight > 0) {
            buckets.add(new FlagDefinition.WeightedVariation(onVariation, onWeight));
        }
        if (offWeight > 0) {
            buckets.add(new FlagDefinition.WeightedVariation(config.getOffVariation(), offWeight));
        }

        FlagDefinition.RolloutConfig rollout = new FlagDefinition.RolloutConfig(buckets);

        return new FlagDefinition(
            flag.getKey(),
            flag.getType(),
            config.isEnabled(),
            config.getOffVariation(),
            config.getDefaultVariation(),
            variations,
            targets,
            rules,
            rollout,
            config.getSalt() != null ? config.getSalt() : "v1"
        );
    }

    @Transactional
    public void recordEvaluation(String flagKey) {
        try {
            // Non-blocking update of lastEvaluatedAt
            flagRepository.findAll().stream()
                .filter(f -> f.getKey().equals(flagKey))
                .findFirst()
                .ifPresent(f -> {
                    f.setLastEvaluatedAt(Instant.now());
                    flagRepository.save(f);
                });
        } catch (Exception ignored) {}
    }

    @Transactional
    public boolean toggleKillSwitch(String projectKey, String envKey, String flagKey, boolean enabled,
                                    String author, String reason, boolean isAdmin) throws Exception {
        Environment env = environmentRepository.findByProjectKeyAndKey(projectKey, envKey)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envKey));

        FlagEnvironmentConfig config = configRepository.findByFlagKeyAndEnvironmentKey(flagKey, envKey)
            .orElseThrow(() -> new IllegalArgumentException("Flag config not found for: " + flagKey));

        // FR7: 2-Person production approval check
        if (env.isRequireApproval() && !isAdmin) {
            Map<String, Object> pendingPayload = Map.of(
                "action", "TOGGLE",
                "enabled", enabled,
                "rolloutPercentage", config.getRolloutPercentage(),
                "defaultVariation", config.getDefaultVariation()
            );
            ApprovalRequest req = new ApprovalRequest(
                projectKey, envKey, flagKey, author,
                reason != null && !reason.isBlank() ? reason : "Toggle state change to " + (enabled ? "ENABLED" : "KILL-SWITCHED"),
                objectMapper.writeValueAsString(pendingPayload)
            );
            approvalRepository.save(req);
            log.info("Production approval request #{} created for flag {}", req.getId(), flagKey);
            return false; // Pending approval
        }

        // Apply immediately
        config.setEnabled(enabled);
        config.setCurrentVersion(config.getCurrentVersion() + 1);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);

        recordVersionAndBroadcast(projectKey, envKey, flagKey, config, author,
            reason != null && !reason.isBlank() ? reason : (enabled ? "Enabled flag" : "Triggered KILL-SWITCH"));

        return true;
    }

    @Transactional
    public boolean updateFlagConfig(String projectKey, String envKey, String flagKey,
                                    int rolloutPercentage, String defaultVariation, String offVariation,
                                    String individualTargetsJson, String rulesJson,
                                    String author, String reason, boolean isAdmin) throws Exception {
        Environment env = environmentRepository.findByProjectKeyAndKey(projectKey, envKey)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envKey));

        FlagEnvironmentConfig config = configRepository.findByFlagKeyAndEnvironmentKey(flagKey, envKey)
            .orElseThrow(() -> new IllegalArgumentException("Flag config not found for: " + flagKey));

        FeatureFlag flag = config.getFlag();

        // Check if 2-person production approval required
        if (env.isRequireApproval() && !isAdmin) {
            Map<String, Object> pendingPayload = Map.of(
                "action", "UPDATE_CONFIG",
                "enabled", config.isEnabled(),
                "rolloutPercentage", rolloutPercentage,
                "defaultVariation", defaultVariation,
                "offVariation", offVariation,
                "individualTargetsJson", individualTargetsJson != null ? individualTargetsJson : "{}",
                "rulesJson", rulesJson != null ? rulesJson : "[]"
            );
            ApprovalRequest req = new ApprovalRequest(
                projectKey, envKey, flagKey, author,
                reason != null && !reason.isBlank() ? reason : "Targeting rule / rollout update",
                objectMapper.writeValueAsString(pendingPayload)
            );
            approvalRepository.save(req);
            return false; // Pending approval
        }

        // Apply configuration changes
        config.setRolloutPercentage(rolloutPercentage);
        if (defaultVariation != null) config.setDefaultVariation(defaultVariation);
        if (offVariation != null) config.setOffVariation(offVariation);
        if (individualTargetsJson != null) config.setIndividualTargetsJson(individualTargetsJson);
        if (rulesJson != null) config.setRulesJson(rulesJson);

        config.setCurrentVersion(config.getCurrentVersion() + 1);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);

        // Flag hygiene: mark 100% rollout timestamp
        if (rolloutPercentage >= 100 && flag.getFullyRolledOutAt() == null) {
            flag.setFullyRolledOutAt(Instant.now());
            flagRepository.save(flag);
        } else if (rolloutPercentage < 100 && flag.getFullyRolledOutAt() != null) {
            flag.setFullyRolledOutAt(null);
            flagRepository.save(flag);
        }

        recordVersionAndBroadcast(projectKey, envKey, flagKey, config, author,
            reason != null && !reason.isBlank() ? reason : "Updated targeting configuration");

        return true;
    }

    @Transactional
    public void rollbackToVersion(String projectKey, String envKey, String flagKey, long targetVersion,
                                  String author) throws Exception {
        FlagVersion version = versionRepository
            .findByProjectKeyAndEnvironmentKeyAndFlagKeyAndVersionNumber(projectKey, envKey, flagKey, targetVersion)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + targetVersion));

        FlagEnvironmentConfig config = configRepository.findByFlagKeyAndEnvironmentKey(flagKey, envKey)
            .orElseThrow(() -> new IllegalArgumentException("Flag config not found for: " + flagKey));

        Map<String, Object> state = objectMapper.readValue(version.getSnapshotJson(), new TypeReference<>() {});
        if (state.containsKey("enabled")) config.setEnabled((Boolean) state.get("enabled"));
        if (state.containsKey("rolloutPercentage")) config.setRolloutPercentage(((Number) state.get("rolloutPercentage")).intValue());
        if (state.containsKey("defaultVariation")) config.setDefaultVariation((String) state.get("defaultVariation"));
        if (state.containsKey("offVariation")) config.setOffVariation((String) state.get("offVariation"));
        if (state.containsKey("individualTargetsJson")) config.setIndividualTargetsJson((String) state.get("individualTargetsJson"));
        if (state.containsKey("rulesJson")) config.setRulesJson((String) state.get("rulesJson"));

        config.setCurrentVersion(config.getCurrentVersion() + 1);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);

        recordVersionAndBroadcast(projectKey, envKey, flagKey, config, author,
            "One-action Rollback to v" + targetVersion);
    }

    public void recordVersionAndBroadcast(String projectKey, String envKey, String flagKey,
                                          FlagEnvironmentConfig config, String author, String reason) throws Exception {
        Map<String, Object> snapshotMap = Map.of(
            "enabled", config.isEnabled(),
            "rolloutPercentage", config.getRolloutPercentage(),
            "defaultVariation", config.getDefaultVariation(),
            "offVariation", config.getOffVariation(),
            "individualTargetsJson", config.getIndividualTargetsJson() != null ? config.getIndividualTargetsJson() : "{}",
            "rulesJson", config.getRulesJson() != null ? config.getRulesJson() : "[]",
            "salt", config.getSalt() != null ? config.getSalt() : "v1"
        );
        String stateJson = objectMapper.writeValueAsString(snapshotMap);

        FlagVersion flagVersion = new FlagVersion(
            projectKey, envKey, flagKey, config.getCurrentVersion(),
            author != null ? author : "system",
            reason != null ? reason : "Configuration updated",
            stateJson
        );
        versionRepository.save(flagVersion);

        globalRevision.incrementAndGet();

        // Broadcast live SSE update immediately to connected Spring Boot clients
        String fullSnapshotJson = objectMapper.writeValueAsString(getSnapshot(projectKey, envKey));
        sseHub.broadcastSnapshot(projectKey, envKey, fullSnapshotJson);
    }

    @Transactional
    public FeatureFlag createFlag(String projectKey, String key, String name, String description, FlagType type, String owner) throws Exception {
        Project project = projectRepository.findByKey(projectKey)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectKey));

        if (flagRepository.existsByProjectKeyAndKey(projectKey, key)) {
            throw new IllegalArgumentException("Flag key already exists: " + key);
        }

        FeatureFlag flag = new FeatureFlag(project, key, name, description, type, owner);
        FeatureFlag saved = flagRepository.save(flag);

        List<Environment> envs = environmentRepository.findByProjectKey(projectKey);
        for (Environment env : envs) {
            FlagEnvironmentConfig cfg = new FlagEnvironmentConfig(saved, env);
            configRepository.save(cfg);
        }

        globalRevision.incrementAndGet();
        for (Environment env : envs) {
            String fullSnapshotJson = objectMapper.writeValueAsString(getSnapshot(projectKey, env.getKey()));
            sseHub.broadcastSnapshot(projectKey, env.getKey(), fullSnapshotJson);
        }
        return saved;
    }

    @Transactional
    public void archiveFlag(String projectKey, String flagKey, String author) throws Exception {
        FeatureFlag flag = flagRepository.findByProjectKeyAndKey(projectKey, flagKey)
            .orElseThrow(() -> new IllegalArgumentException("Flag not found: " + flagKey));
        flag.setArchived(true);
        flagRepository.save(flag);

        globalRevision.incrementAndGet();

        List<Environment> envs = environmentRepository.findByProjectKey(projectKey);
        for (Environment env : envs) {
            String fullSnapshotJson = objectMapper.writeValueAsString(getSnapshot(projectKey, env.getKey()));
            sseHub.broadcastSnapshot(projectKey, env.getKey(), fullSnapshotJson);
        }
    }

    private Map<String, Object> parseVariations(String json, FlagType type) {
        if (json == null || json.isBlank()) {
            return type == FlagType.BOOLEAN ? Map.of("on", true, "off", false) : Map.of("default", "default");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("on", true, "off", false);
        }
    }

    private Map<String, Set<String>> parseTargets(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<FlagDefinition.SegmentRule> parseRules(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
