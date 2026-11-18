package io.flagship.starter.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "flagship")
public class FlagShipProperties {
    private boolean enabled = true;
    private String serverUrl = "http://localhost:8080";
    private String projectKey = "default";
    private String environment = "production";
    private String apiKey = "fs_live_default";
    private Path snapshotFile = Path.of(System.getProperty("user.home"), ".flagship", "snapshot.json");
    private Duration startupTimeout = Duration.ofMillis(1800);
    private Duration pollInterval = Duration.ofSeconds(15);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Path getSnapshotFile() { return snapshotFile; }
    public void setSnapshotFile(Path snapshotFile) { this.snapshotFile = snapshotFile; }
    public Duration getStartupTimeout() { return startupTimeout; }
    public void setStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout.compareTo(Duration.ofSeconds(2)) > 0
            ? Duration.ofSeconds(2) : startupTimeout;
    }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
}
