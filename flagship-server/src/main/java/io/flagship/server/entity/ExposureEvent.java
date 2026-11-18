package io.flagship.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fs_exposure_events", indexes = {
    @Index(name = "idx_exp_flag_var", columnList = "flag_key, variation, environment_key")
})
public class ExposureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "environment_key", nullable = false, length = 32)
    private String environmentKey;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    @Column(nullable = false, length = 64)
    private String variation;

    @Column(name = "user_key", nullable = false, length = 128)
    private String userKey;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public ExposureEvent() {}

    public ExposureEvent(String projectKey, String environmentKey, String flagKey, String variation, String userKey, Instant timestamp) {
        this.projectKey = projectKey;
        this.environmentKey = environmentKey;
        this.flagKey = flagKey;
        this.variation = variation;
        this.userKey = userKey;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public Long getId() { return id; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getEnvironmentKey() { return environmentKey; }
    public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getVariation() { return variation; }
    public void setVariation(String variation) { this.variation = variation; }
    public String getUserKey() { return userKey; }
    public void setUserKey(String userKey) { this.userKey = userKey; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
