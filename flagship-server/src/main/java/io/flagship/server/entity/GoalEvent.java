package io.flagship.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fs_goal_events", indexes = {
    @Index(name = "idx_goal_flag_name", columnList = "flag_key, goal_name, environment_key")
})
public class GoalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 64)
    private String projectKey;

    @Column(name = "environment_key", nullable = false, length = 32)
    private String environmentKey;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String flagKey;

    @Column(name = "goal_name", nullable = false, length = 64)
    private String goalName;

    @Column(length = 64)
    private String variation;

    @Column(name = "user_key", nullable = false, length = 128)
    private String userKey;

    @Column(name = "event_value", nullable = false)
    private double value = 1.0;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public GoalEvent() {}

    public GoalEvent(String projectKey, String environmentKey, String flagKey, String goalName, String variation, String userKey, double value, Instant timestamp) {
        this.projectKey = projectKey;
        this.environmentKey = environmentKey;
        this.flagKey = flagKey;
        this.goalName = goalName;
        this.variation = variation;
        this.userKey = userKey;
        this.value = value;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public Long getId() { return id; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getEnvironmentKey() { return environmentKey; }
    public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }
    public String getVariation() { return variation; }
    public void setVariation(String variation) { this.variation = variation; }
    public String getUserKey() { return userKey; }
    public void setUserKey(String userKey) { this.userKey = userKey; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
