package io.flagship.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fs_environments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "env_key"})
})
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(name = "env_key", nullable = false, length = 32)
    private String key; // development, staging, production

    @Column(nullable = false, length = 64)
    private String name; // Development, Staging, Production

    @Column(nullable = false, unique = true, length = 128)
    private String apiKey;

    @Column(nullable = false)
    private boolean requireApproval = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Environment() {}

    public Environment(Project project, String key, String name, String apiKey, boolean requireApproval) {
        this.project = project;
        this.key = key;
        this.name = name;
        this.apiKey = apiKey;
        this.requireApproval = requireApproval;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public boolean isRequireApproval() { return requireApproval; }
    public void setRequireApproval(boolean requireApproval) { this.requireApproval = requireApproval; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
