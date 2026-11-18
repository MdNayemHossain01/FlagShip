package io.flagship.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "fs_approval_requests")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "env_key", nullable = false, length = 32)
    private String environmentKey;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    @Column(nullable = false, length = 64)
    private String author;

    @Column(nullable = false, length = 512)
    private String reason;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String targetConfigJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(length = 64)
    private String approver;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    @Column
    private Instant reviewedAt;

    @Column(length = 512)
    private String reviewComment;

    public ApprovalRequest() {}

    public ApprovalRequest(String projectKey, String environmentKey, String flagKey, String author, String reason, String targetConfigJson) {
        this.projectKey = projectKey;
        this.environmentKey = environmentKey;
        this.flagKey = flagKey;
        this.author = author;
        this.reason = reason;
        this.targetConfigJson = targetConfigJson;
        this.status = ApprovalStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getEnvironmentKey() { return environmentKey; }
    public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getTargetConfigJson() { return targetConfigJson; }
    public void setTargetConfigJson(String targetConfigJson) { this.targetConfigJson = targetConfigJson; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
}
