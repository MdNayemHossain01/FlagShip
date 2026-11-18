package io.flagship.server.repository;

import io.flagship.server.entity.FlagEnvironmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FlagEnvironmentConfigRepository extends JpaRepository<FlagEnvironmentConfig, Long> {
    Optional<FlagEnvironmentConfig> findByFlagKeyAndEnvironmentKey(String flagKey, String environmentKey);
    List<FlagEnvironmentConfig> findByEnvironmentProjectKeyAndEnvironmentKey(String projectKey, String environmentKey);
    List<FlagEnvironmentConfig> findByFlagId(Long flagId);
}
