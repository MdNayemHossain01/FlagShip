package io.flagship.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.server.entity.ScheduledChange;
import io.flagship.server.repository.ScheduledChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledChangeService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledChangeService.class);

    private final ScheduledChangeRepository scheduledChangeRepo;
    private final FlagService flagService;
    private final ObjectMapper objectMapper;

    public ScheduledChangeService(
            ScheduledChangeRepository scheduledChangeRepo,
            FlagService flagService,
            ObjectMapper objectMapper) {
        this.scheduledChangeRepo = scheduledChangeRepo;
        this.flagService = flagService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScheduledChange scheduleChange(
            String projectKey, String envKey, String flagKey, String author,
            Instant scheduledTime, Map<String, Object> payload) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);
        ScheduledChange sc = new ScheduledChange(projectKey, envKey, flagKey, author, scheduledTime, payloadJson);
        return scheduledChangeRepo.save(sc);
    }

    public List<ScheduledChange> getPendingChanges(String projectKey, String envKey) {
        return scheduledChangeRepo.findByProjectKeyAndEnvironmentKeyOrderByScheduledTimeDesc(projectKey, envKey);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processDueChanges() {
        Instant now = Instant.now();
        List<ScheduledChange> due = scheduledChangeRepo.findByExecutedFalseAndScheduledTimeBefore(now);
        for (ScheduledChange sc : due) {
            try {
                log.info("Executing scheduled change #{} for flag {} in {}:{}", sc.getId(), sc.getFlagKey(), sc.getProjectKey(), sc.getEnvironmentKey());
                Map<String, Object> payload = objectMapper.readValue(sc.getChangePayloadJson(), new TypeReference<>() {});

                if (payload.containsKey("enabled")) {
                    boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
                    String reason = (String) payload.getOrDefault("reason", "Scheduled kill-switch toggle");
                    flagService.toggleKillSwitch(sc.getProjectKey(), sc.getEnvironmentKey(), sc.getFlagKey(), enabled, sc.getAuthor(), reason, true);
                }

                if (payload.containsKey("rolloutPercentage")) {
                    int rollout = ((Number) payload.get("rolloutPercentage")).intValue();
                    String defVar = (String) payload.getOrDefault("defaultVariation", "off");
                    String offVar = (String) payload.getOrDefault("offVariation", "off");
                    String targets = (String) payload.getOrDefault("individualTargetsJson", "{}");
                    String rules = (String) payload.getOrDefault("rulesJson", "[]");
                    String reason = (String) payload.getOrDefault("reason", "Scheduled rollout update");
                    flagService.updateFlagConfig(sc.getProjectKey(), sc.getEnvironmentKey(), sc.getFlagKey(),
                            rollout, defVar, offVar, targets, rules, sc.getAuthor(), reason, true);
                }

                sc.setExecuted(true);
                sc.setExecutedAt(Instant.now());
                scheduledChangeRepo.save(sc);
                log.info("Scheduled change #{} successfully applied.", sc.getId());
            } catch (Exception ex) {
                log.error("Failed executing scheduled change #{}: {}", sc.getId(), ex.getMessage(), ex);
            }
        }
    }
}
