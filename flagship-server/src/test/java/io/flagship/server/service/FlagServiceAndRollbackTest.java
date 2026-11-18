package io.flagship.server.service;

import io.flagship.core.model.FlagType;
import io.flagship.server.entity.*;
import io.flagship.server.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlagServiceAndRollbackTest {

    @Autowired
    private FlagService flagService;

    @Autowired
    private FeatureFlagRepository flagRepo;

    @Autowired
    private FlagEnvironmentConfigRepository configRepo;

    @Autowired
    private EnvironmentRepository envRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private FlagVersionRepository versionRepo;

    private String testFlagKey = "rollback-test-flag";

    @BeforeEach
    void setUp() {
        Project project = projectRepo.findByKey("default")
            .orElseGet(() -> projectRepo.save(new Project("default", "Main", "Desc")));

        Environment devEnv = envRepo.findByProjectKeyAndKey("default", "development")
            .orElseGet(() -> envRepo.save(new Environment(project, "development", "Development", "fs_dev_key", false)));

        FeatureFlag flag = flagRepo.findByProjectKeyAndKey("default", testFlagKey)
            .orElseGet(() -> flagRepo.save(new FeatureFlag(project, testFlagKey, "Rollback Flag", "Desc", FlagType.BOOLEAN, "author")));

        configRepo.findByFlagKeyAndEnvironmentKey(testFlagKey, "development")
            .orElseGet(() -> {
                FlagEnvironmentConfig config = new FlagEnvironmentConfig(flag, devEnv);
                config.setEnabled(true);
                config.setRolloutPercentage(10);
                config.setCurrentVersion(1L);
                return configRepo.save(config);
            });
    }

    @Test
    void testToggleKillSwitch_ImmediateExecutionInDevelopment() throws Exception {
        boolean applied = flagService.toggleKillSwitch("default", "development", testFlagKey, false, "admin", "Kill switch engaged", true);
        assertTrue(applied);

        FlagEnvironmentConfig updated = configRepo.findByFlagKeyAndEnvironmentKey(testFlagKey, "development").orElseThrow();
        assertFalse(updated.isEnabled());
        assertTrue(updated.getCurrentVersion() > 1L);
    }

    @Test
    void testOneActionRollback_RestoresTargetVersionState() throws Exception {
        // Change rollout to 80% (creates v2)
        flagService.updateFlagConfig("default", "development", testFlagKey, 80, "on", "off", "{}", "[]", "admin", "Change to 80%", true);

        FlagEnvironmentConfig configAfterUpdate = configRepo.findByFlagKeyAndEnvironmentKey(testFlagKey, "development").orElseThrow();
        assertEquals(80, configAfterUpdate.getRolloutPercentage());
        long v2Number = configAfterUpdate.getCurrentVersion();

        // Change rollout to 100% (creates v3)
        flagService.updateFlagConfig("default", "development", testFlagKey, 100, "on", "off", "{}", "[]", "admin", "Change to 100%", true);
        FlagEnvironmentConfig configAt100 = configRepo.findByFlagKeyAndEnvironmentKey(testFlagKey, "development").orElseThrow();
        assertEquals(100, configAt100.getRolloutPercentage());

        // Now perform 1-Action Rollback back to v2 (which was 80%)
        flagService.rollbackToVersion("default", "development", testFlagKey, v2Number, "admin");

        FlagEnvironmentConfig rolledBack = configRepo.findByFlagKeyAndEnvironmentKey(testFlagKey, "development").orElseThrow();
        assertEquals(80, rolledBack.getRolloutPercentage(), "Rollout should be restored to 80% after rollback!");
    }
}
