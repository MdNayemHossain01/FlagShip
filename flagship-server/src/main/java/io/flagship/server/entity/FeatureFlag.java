package io.flagship.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.flagship.core.model.FlagType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fs_feature_flags", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "flag_key"})
})
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(name = "flag_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FlagType type;

    @Column(nullable = false, length = 64)
    private String owner;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant lastEvaluatedAt;

    @Column
    private Instant fullyRolledOutAt;

    @Column(nullable = false)
    private boolean archived = false;

    @OneToMany(mappedBy = "flag", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlagEnvironmentConfig> environmentConfigs = new ArrayList<>();

    public FeatureFlag() {}

    public FeatureFlag(Project project, String key, String name, String description, FlagType type, String owner) {
        this.project = project;
        this.key = key;
        this.name = name;
        this.description = description;
        this.type = type;
        this.owner = owner;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.archived = false;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public FlagType getType() { return type; }
    public void setType(FlagType type) { this.type = type; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
    public Instant getFullyRolledOutAt() { return fullyRolledOutAt; }
    public void setFullyRolledOutAt(Instant fullyRolledOutAt) { this.fullyRolledOutAt = fullyRolledOutAt; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public List<FlagEnvironmentConfig> getEnvironmentConfigs() { return environmentConfigs; }
    public void setEnvironmentConfigs(List<FlagEnvironmentConfig> environmentConfigs) { this.environmentConfigs = environmentConfigs; }
}
