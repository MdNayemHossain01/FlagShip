package io.flagship.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "fs_flag_env_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"flag_id", "environment_id"})
})
public class FlagEnvironmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flag_id", nullable = false)
    @JsonIgnore
    private FeatureFlag flag;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false, length = 64)
    private String offVariation = "off";

    @Column(nullable = false, length = 64)
    private String defaultVariation = "off";

    @Column(nullable = false)
    private int rolloutPercentage = 0;

    @Column(length = 64)
    private String salt = "v1";

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String variationsJson; // Map<String, Object>

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String individualTargetsJson; // Map<String, Set<String>>

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String rulesJson; // List<SegmentRule>

    @Column(nullable = false)
    private long currentVersion = 1L;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public FlagEnvironmentConfig() {}

    public FlagEnvironmentConfig(FeatureFlag flag, Environment environment) {
        this.flag = flag;
        this.environment = environment;
        this.enabled = false;
        this.offVariation = "off";
        this.defaultVariation = "off";
        this.rolloutPercentage = 0;
        this.salt = "v1";
        this.variationsJson = "{\"on\":true,\"off\":false}";
        this.individualTargetsJson = "{}";
        this.rulesJson = "[]";
        this.currentVersion = 1L;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public FeatureFlag getFlag() { return flag; }
    public void setFlag(FeatureFlag flag) { this.flag = flag; }
    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getOffVariation() { return offVariation; }
    public void setOffVariation(String offVariation) { this.offVariation = offVariation; }
    public String getDefaultVariation() { return defaultVariation; }
    public void setDefaultVariation(String defaultVariation) { this.defaultVariation = defaultVariation; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public void setRolloutPercentage(int rolloutPercentage) { this.rolloutPercentage = rolloutPercentage; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public String getVariationsJson() { return variationsJson; }
    public void setVariationsJson(String variationsJson) { this.variationsJson = variationsJson; }
    public String getIndividualTargetsJson() { return individualTargetsJson; }
    public void setIndividualTargetsJson(String individualTargetsJson) { this.individualTargetsJson = individualTargetsJson; }
    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
    public long getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(long currentVersion) { this.currentVersion = currentVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
