package io.flagship.server.repository;

import io.flagship.server.entity.ScheduledChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ScheduledChangeRepository extends JpaRepository<ScheduledChange, Long> {
    List<ScheduledChange> findByExecutedFalseAndScheduledTimeBefore(Instant now);
    List<ScheduledChange> findByProjectKeyAndEnvironmentKeyOrderByScheduledTimeDesc(String projectKey, String environmentKey);
}
