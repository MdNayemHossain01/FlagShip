package io.flagship.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "fs_scheduled_changes")
public class ScheduledChange {

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

    @Column(nullable = false)
    private Instant scheduledTime;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String changePayloadJson;

    @Column(nullable = false)
    private boolean executed = false;

    @Column
    private Instant executedAt;

    public ScheduledChange() {}

    public ScheduledChange(String projectKey, String environmentKey, String flagKey, String author, Instant scheduledTime, String changePayloadJson) {
        this.projectKey = projectKey;
        this.environmentKey = environmentKey;
        this.flagKey = flagKey;
        this.author = author;
        this.scheduledTime = scheduledTime;
        this.changePayloadJson = changePayloadJson;
        this.executed = false;
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
    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }
    public String getChangePayloadJson() { return changePayloadJson; }
    public void setChangePayloadJson(String changePayloadJson) { this.changePayloadJson = changePayloadJson; }
    public boolean isExecuted() { return executed; }
    public void setExecuted(boolean executed) { this.executed = executed; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
