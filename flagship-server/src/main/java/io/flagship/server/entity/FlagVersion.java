package io.flagship.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "fs_flag_versions", indexes = {
    @Index(name = "idx_flag_version", columnList = "project_key, env_key, flag_key, version_number")
})
public class FlagVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "env_key", nullable = false, length = 32)
    private String environmentKey;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    @Column(name = "version_number", nullable = false)
    private long versionNumber;

    @Column(nullable = false, length = 64)
    private String changeAuthor;

    @Column(nullable = false, length = 512)
    private String changeReason;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String snapshotJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public FlagVersion() {}

    public FlagVersion(String projectKey, String environmentKey, String flagKey, long versionNumber,
                       String changeAuthor, String changeReason, String snapshotJson) {
        this.projectKey = projectKey;
        this.environmentKey = environmentKey;
        this.flagKey = flagKey;
        this.versionNumber = versionNumber;
        this.changeAuthor = changeAuthor;
        this.changeReason = changeReason;
        this.snapshotJson = snapshotJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getEnvironmentKey() { return environmentKey; }
    public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public long getVersionNumber() { return versionNumber; }
    public void setVersionNumber(long versionNumber) { this.versionNumber = versionNumber; }
    public String getChangeAuthor() { return changeAuthor; }
    public void setChangeAuthor(String changeAuthor) { this.changeAuthor = changeAuthor; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
