package io.flagship.server.repository;

import io.flagship.server.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    List<FeatureFlag> findByProjectKey(String projectKey);
    List<FeatureFlag> findByProjectKeyAndArchivedFalse(String projectKey);
    Optional<FeatureFlag> findByProjectKeyAndKey(String projectKey, String key);
    boolean existsByProjectKeyAndKey(String projectKey, String key);

    // Flag hygiene queries
    List<FeatureFlag> findByArchivedFalseAndLastEvaluatedAtBefore(Instant cutoff);
    List<FeatureFlag> findByArchivedFalseAndLastEvaluatedAtIsNullAndCreatedAtBefore(Instant cutoff);
    List<FeatureFlag> findByArchivedFalseAndFullyRolledOutAtBefore(Instant cutoff);
}
