package io.flagship.server.repository;

import io.flagship.server.entity.GoalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoalEventRepository extends JpaRepository<GoalEvent, Long> {
    @Query("SELECT g.variation, COUNT(g.id), COUNT(DISTINCT g.userKey), COALESCE(SUM(g.value), 0.0) FROM GoalEvent g " +
           "WHERE g.projectKey = :projectKey AND g.environmentKey = :envKey AND g.flagKey = :flagKey " +
           "GROUP BY g.variation")
    List<Object[]> getGoalCountsByVariation(
        @Param("projectKey") String projectKey,
        @Param("envKey") String envKey,
        @Param("flagKey") String flagKey
    );
}
