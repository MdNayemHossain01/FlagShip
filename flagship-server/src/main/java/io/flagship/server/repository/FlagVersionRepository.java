package io.flagship.server.repository;

import io.flagship.server.entity.FlagVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FlagVersionRepository extends JpaRepository<FlagVersion, Long> {
    List<FlagVersion> findByProjectKeyAndEnvironmentKeyAndFlagKeyOrderByVersionNumberDesc(
        String projectKey, String environmentKey, String flagKey);

    List<FlagVersion> findTop50ByOrderByCreatedAtDesc();

    Optional<FlagVersion> findByProjectKeyAndEnvironmentKeyAndFlagKeyAndVersionNumber(
        String projectKey, String environmentKey, String flagKey, long versionNumber);
}
