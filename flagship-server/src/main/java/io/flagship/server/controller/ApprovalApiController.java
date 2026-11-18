package io.flagship.server.controller;

import io.flagship.server.approval.ApprovalService;
import io.flagship.server.entity.ApprovalRequest;
import io.flagship.server.entity.ApprovalStatus;
import io.flagship.server.repository.ApprovalRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/approvals")
@CrossOrigin(origins = "*")
public class ApprovalApiController {

    private final ApprovalRequestRepository approvalRepo;
    private final ApprovalService approvalService;

    public ApprovalApiController(ApprovalRequestRepository approvalRepo, ApprovalService approvalService) {
        this.approvalRepo = approvalRepo;
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<ApprovalRequest> listPending() {
        return approvalRepo.findByStatusOrderByRequestedAtDesc(ApprovalStatus.PENDING);
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<?> decide(
            @PathVariable Long id,
            @RequestParam String decision, // APPROVE or REJECT
            @RequestParam(required = false) String comment,
            Authentication auth) throws Exception {

        String approver = auth != null ? auth.getName() : "approver";
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if ("APPROVE".equalsIgnoreCase(decision)) {
            approvalService.approve(id, approver, comment, isAdmin);
            return ResponseEntity.ok(Map.of("status", "APPROVED", "id", id));
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            approvalService.reject(id, approver, comment);
            return ResponseEntity.ok(Map.of("status", "REJECTED", "id", id));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown decision: " + decision));
        }
    }
}
