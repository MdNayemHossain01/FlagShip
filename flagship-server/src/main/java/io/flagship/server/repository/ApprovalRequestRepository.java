package io.flagship.server.repository;

import io.flagship.server.entity.ApprovalRequest;
import io.flagship.server.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findByStatusOrderByRequestedAtDesc(ApprovalStatus status);
    List<ApprovalRequest> findByProjectKeyAndEnvironmentKeyOrderByRequestedAtDesc(String projectKey, String environmentKey);
    long countByStatus(ApprovalStatus status);
}
