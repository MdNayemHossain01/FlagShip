package io.flagship.server.repository;

import io.flagship.server.entity.ExposureEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExposureEventRepository extends JpaRepository<ExposureEvent, Long> {
    long countByProjectKeyAndEnvironmentKeyAndFlagKey(String projectKey, String environmentKey, String flagKey);

    @Query("SELECT e.variation, COUNT(e.id), COUNT(DISTINCT e.userKey) FROM ExposureEvent e " +
           "WHERE e.projectKey = :projectKey AND e.environmentKey = :envKey AND e.flagKey = :flagKey " +
           "GROUP BY e.variation")
    List<Object[]> getExposureCountsByVariation(
        @Param("projectKey") String projectKey,
        @Param("envKey") String envKey,
        @Param("flagKey") String flagKey
    );
}
