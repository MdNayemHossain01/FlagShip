package io.flagship.server.repository;

import io.flagship.server.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
    List<Environment> findByProjectKey(String projectKey);
    Optional<Environment> findByProjectKeyAndKey(String projectKey, String key);
    Optional<Environment> findByApiKey(String apiKey);
}
