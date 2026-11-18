package io.flagship.server.approval;

import io.flagship.core.model.FlagType;
import io.flagship.server.entity.*;
import io.flagship.server.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApprovalWorkflowTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRequestRepository approvalRepo;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private EnvironmentRepository envRepo;

    @Autowired
    private FeatureFlagRepository flagRepo;

    @Autowired
    private FlagEnvironmentConfigRepository configRepo;

    private Long testRequestId;

    @BeforeEach
    void setUp() {
        System.out.println("=== SETUP START ===");
        Project p = projectRepo.findByKey("default").orElseGet(() -> projectRepo.save(new Project("default", "Main", "Desc")));
        Environment prod = envRepo.findByProjectKeyAndKey("default", "production")
            .orElseGet(() -> envRepo.save(new Environment(p, "production", "Production", "fs_live_key", true)));

        FeatureFlag flag = flagRepo.findByProjectKeyAndKey("default", "appr-flag")
            .orElseGet(() -> flagRepo.save(new FeatureFlag(p, "appr-flag", "Approval Flag", "Desc", FlagType.BOOLEAN, "author-alice")));

        FlagEnvironmentConfig config = configRepo.findByFlagKeyAndEnvironmentKey("appr-flag", "production")
            .orElseGet(() -> {
                FlagEnvironmentConfig c = new FlagEnvironmentConfig(flag, prod);
                c.setEnabled(false);
                c.setRolloutPercentage(10);
                return configRepo.save(c);
            });

        ApprovalRequest req = new ApprovalRequest(
            "default", "production", "appr-flag", "author-alice",
            "Increase rollout to 50%", "{\"enabled\":true,\"rolloutPercentage\":50}"
        );
        req = approvalRepo.save(req);
        testRequestId = req.getId();
        System.out.println("=== SETUP DONE, requestId=" + testRequestId + " ===");
    }

    @Test
    void testSelfApprovalViolation_ThrowsSecurityException() {
        System.out.println("=== RUNNING testSelfApprovalViolation ===");
        assertThrows(SecurityException.class, () ->
            approvalService.approve(testRequestId, "author-alice", "Trying to self-approve", false)
        );
        System.out.println("=== FINISHED testSelfApprovalViolation ===");
    }

    @Test
    void testIndependentApprover_SuccessfullyApproves() throws Exception {
        System.out.println("=== RUNNING testIndependentApprover ===");
        approvalService.approve(testRequestId, "approver-bob", "Verified in staging", false);

        ApprovalRequest updated = approvalRepo.findById(testRequestId).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, updated.getStatus());
        assertEquals("approver-bob", updated.getApprover());

        FlagEnvironmentConfig config = configRepo.findByFlagKeyAndEnvironmentKey("appr-flag", "production").orElseThrow();
        assertTrue(config.isEnabled());
        assertEquals(50, config.getRolloutPercentage());
        System.out.println("=== FINISHED testIndependentApprover ===");
    }

    @Test
    void testRejectRequest_MarksRejected() {
        System.out.println("=== RUNNING testRejectRequest ===");
        approvalService.reject(testRequestId, "approver-bob", "Needs more test coverage");

        ApprovalRequest updated = approvalRepo.findById(testRequestId).orElseThrow();
        assertEquals(ApprovalStatus.REJECTED, updated.getStatus());
        assertEquals("approver-bob", updated.getApprover());
        assertEquals("Needs more test coverage", updated.getReviewComment());
        System.out.println("=== FINISHED testRejectRequest ===");
    }
}
