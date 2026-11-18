package io.flagship.server.approval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.server.entity.ApprovalRequest;
import io.flagship.server.entity.ApprovalStatus;
import io.flagship.server.entity.FlagEnvironmentConfig;
import io.flagship.server.repository.ApprovalRequestRepository;
import io.flagship.server.repository.FlagEnvironmentConfigRepository;
import io.flagship.server.service.FlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class ApprovalService {
    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRequestRepository approvalRepository;
    private final FlagEnvironmentConfigRepository configRepository;
    private final FlagService flagService;
    private final ObjectMapper objectMapper;

    public ApprovalService(ApprovalRequestRepository approvalRepository,
                           FlagEnvironmentConfigRepository configRepository,
                           FlagService flagService,
                           ObjectMapper objectMapper) {
        this.approvalRepository = approvalRepository;
        this.configRepository = configRepository;
        this.flagService = flagService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void approve(Long requestId, String approverUsername, String comment, boolean isAdmin) throws Exception {
        ApprovalRequest req = approvalRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + requestId));

        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request is already " + req.getStatus());
        }

        // Two-person rule enforcement: author cannot approve their own change unless ADMIN
        if (approverUsername.equalsIgnoreCase(req.getAuthor()) && !isAdmin) {
            throw new SecurityException("Two-person rule violation: Author cannot approve their own production change!");
        }

        FlagEnvironmentConfig config = configRepository
            .findByFlagKeyAndEnvironmentKey(req.getFlagKey(), req.getEnvironmentKey())
            .orElseThrow(() -> new IllegalArgumentException("Flag config not found for: " + req.getFlagKey()));

        Map<String, Object> changes = objectMapper.readValue(req.getTargetConfigJson(), new TypeReference<>() {});
        if (changes.containsKey("enabled")) config.setEnabled((Boolean) changes.get("enabled"));
        if (changes.containsKey("rolloutPercentage")) config.setRolloutPercentage(((Number) changes.get("rolloutPercentage")).intValue());
        if (changes.containsKey("defaultVariation")) config.setDefaultVariation((String) changes.get("defaultVariation"));
        if (changes.containsKey("offVariation")) config.setOffVariation((String) changes.get("offVariation"));
        if (changes.containsKey("individualTargetsJson")) config.setIndividualTargetsJson((String) changes.get("individualTargetsJson"));
        if (changes.containsKey("rulesJson")) config.setRulesJson((String) changes.get("rulesJson"));

        config.setCurrentVersion(config.getCurrentVersion() + 1);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);

        req.setStatus(ApprovalStatus.APPROVED);
        req.setApprover(approverUsername);
        req.setReviewedAt(Instant.now());
        req.setReviewComment(comment != null ? comment : "Approved by " + approverUsername);
        approvalRepository.save(req);

        flagService.recordVersionAndBroadcast(req.getProjectKey(), req.getEnvironmentKey(), req.getFlagKey(),
            config, approverUsername, "Approved Change Request #" + req.getId() + " (requested by " + req.getAuthor() + "): " + req.getReason());

        log.info("Approval request #{} APPROVED by {}", req.getId(), approverUsername);
    }

    @Transactional
    public void reject(Long requestId, String approverUsername, String comment) {
        ApprovalRequest req = approvalRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + requestId));

        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request is already " + req.getStatus());
        }

        req.setStatus(ApprovalStatus.REJECTED);
        req.setApprover(approverUsername);
        req.setReviewedAt(Instant.now());
        req.setReviewComment(comment != null ? comment : "Rejected by " + approverUsername);
        approvalRepository.save(req);

        log.info("Approval request #{} REJECTED by {}", req.getId(), approverUsername);
    }
}
