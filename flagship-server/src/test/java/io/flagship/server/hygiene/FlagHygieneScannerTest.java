package io.flagship.server.hygiene;

import io.flagship.core.model.FlagType;
import io.flagship.server.entity.FeatureFlag;
import io.flagship.server.entity.Project;
import io.flagship.server.repository.FeatureFlagRepository;
import io.flagship.server.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlagHygieneScannerTest {

    @Autowired
    private FlagHygieneScanner scanner;

    @Autowired
    private FeatureFlagRepository flagRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Test
    void testDetectStaleFlags_InactiveAndFullyRolledOut() {
        Project project = projectRepo.findByKey("default")
            .orElseGet(() -> projectRepo.save(new Project("default", "Main", "Desc")));

        // Stale flag 1: Not evaluated in 35 days
        FeatureFlag inactiveFlag = new FeatureFlag(project, "hygiene-old-feature", "Old Feature", "Desc", FlagType.BOOLEAN, "dev-team");
        inactiveFlag.setLastEvaluatedAt(Instant.now().minus(Duration.ofDays(35)));
        flagRepo.save(inactiveFlag);

        // Stale flag 2: 100% rolled out for 18 days
        FeatureFlag permanentFlag = new FeatureFlag(project, "hygiene-perm-feature", "Perm Feature", "Desc", FlagType.BOOLEAN, "lead-eng");
        permanentFlag.setFullyRolledOutAt(Instant.now().minus(Duration.ofDays(18)));
        flagRepo.save(permanentFlag);

        List<FlagHygieneScanner.StaleFlagReport> reports = scanner.scanForStaleFlags();

        assertTrue(reports.stream().anyMatch(r -> r.flagKey().equals("hygiene-old-feature")));
        assertTrue(reports.stream().anyMatch(r -> r.flagKey().equals("hygiene-perm-feature")));
    }
}
