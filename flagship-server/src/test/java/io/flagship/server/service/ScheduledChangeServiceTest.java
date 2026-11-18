package io.flagship.server.service;

import io.flagship.core.model.FlagType;
import io.flagship.server.entity.*;
import io.flagship.server.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ScheduledChangeServiceTest {

    @Autowired
    private ScheduledChangeService scheduledService;

    @Autowired
    private ScheduledChangeRepository scheduledRepo;

    @Autowired
    private FeatureFlagRepository flagRepo;

    @Autowired
    private FlagEnvironmentConfigRepository configRepo;

    @Autowired
    private EnvironmentRepository envRepo;

    @Autowired
    private ProjectRepository projectRepo;

    private final String flagKey = "scheduled-test-flag";

    @BeforeEach
    void setUp() {
        Project project = projectRepo.findByKey("default")
            .orElseGet(() -> projectRepo.save(new Project("default", "Main", "Desc")));

        Environment devEnv = envRepo.findByProjectKeyAndKey("default", "development")
            .orElseGet(() -> envRepo.save(new Environment(project, "development", "Development", "fs_dev_sched", false)));

        FeatureFlag flag = flagRepo.findByProjectKeyAndKey("default", flagKey)
            .orElseGet(() -> flagRepo.save(new FeatureFlag(project, flagKey, "Sched Flag", "Desc", FlagType.BOOLEAN, "author")));

        configRepo.findByFlagKeyAndEnvironmentKey(flagKey, "development")
            .orElseGet(() -> {
                FlagEnvironmentConfig config = new FlagEnvironmentConfig(flag, devEnv);
                config.setEnabled(true);
                config.setRolloutPercentage(10);
                config.setCurrentVersion(1L);
                return configRepo.save(config);
            });
    }

    @Test
    void testProcessDueChanges_AppliesPastScheduledChange() throws Exception {
        // Schedule a rollout update to 75% set in the past
        Instant scheduledTime = Instant.now().minusSeconds(10);
        ScheduledChange sc = scheduledService.scheduleChange(
            "default", "development", flagKey, "release-engineer",
            scheduledTime, Map.of("rolloutPercentage", 75, "reason", "Auto gradual release")
        );
        assertNotNull(sc.getId());
        assertFalse(sc.isExecuted());

        // Process due changes
        scheduledService.processDueChanges();

        // Verify scheduled change marked executed
        ScheduledChange updatedSc = scheduledRepo.findById(sc.getId()).orElseThrow();
        assertTrue(updatedSc.isExecuted());
        assertNotNull(updatedSc.getExecutedAt());

        // Verify flag config received 75% rollout
        FlagEnvironmentConfig cfg = configRepo.findByFlagKeyAndEnvironmentKey(flagKey, "development").orElseThrow();
        assertEquals(75, cfg.getRolloutPercentage());
    }
}
