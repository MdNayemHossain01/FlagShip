package io.flagship.server.hygiene;

import io.flagship.server.entity.FeatureFlag;
import io.flagship.server.repository.FeatureFlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FlagHygieneScanner {
    private static final Logger log = LoggerFactory.getLogger(FlagHygieneScanner.class);

    public record StaleFlagReport(
        Long flagId,
        String projectKey,
        String flagKey,
        String flagName,
        String owner,
        Instant lastEvaluatedAt,
        Instant fullyRolledOutAt,
        String staleReason,
        String recommendation
    ) {}

    private final FeatureFlagRepository flagRepository;

    public FlagHygieneScanner(FeatureFlagRepository flagRepository) {
        this.flagRepository = flagRepository;
    }

    public List<StaleFlagReport> scanForStaleFlags() {
        Instant now = Instant.now();
        Instant evalCutoff = now.minus(Duration.ofDays(30));
        Instant rolloutCutoff = now.minus(Duration.ofDays(14));

        List<StaleFlagReport> reports = new ArrayList<>();

        // Criteria 1: Flags evaluated but not in the last 30 days
        List<FeatureFlag> inactiveEvaluated = flagRepository.findByArchivedFalseAndLastEvaluatedAtBefore(evalCutoff);
        for (FeatureFlag f : inactiveEvaluated) {
            reports.add(new StaleFlagReport(
                f.getId(),
                f.getProject().getKey(),
                f.getKey(),
                f.getName(),
                f.getOwner(),
                f.getLastEvaluatedAt(),
                f.getFullyRolledOutAt(),
                "Inactive: No evaluations recorded for over 30 days.",
                "Recommend archiving or deprecating code paths associated with this flag."
            ));
        }

        // Criteria 1b: Flags created over 30 days ago that have NEVER been evaluated
        List<FeatureFlag> neverEvaluated = flagRepository.findByArchivedFalseAndLastEvaluatedAtIsNullAndCreatedAtBefore(evalCutoff);
        for (FeatureFlag f : neverEvaluated) {
            reports.add(new StaleFlagReport(
                f.getId(),
                f.getProject().getKey(),
                f.getKey(),
                f.getName(),
                f.getOwner(),
                null,
                f.getFullyRolledOutAt(),
                "Zombie Flag: Created > 30 days ago and has NEVER received any evaluations.",
                "Verify whether feature was deployed or abandon unused flag."
            ));
        }

        // Criteria 2: Fully rolled out (100%) for > 14 days
        List<FeatureFlag> permanentRollouts = flagRepository.findByArchivedFalseAndFullyRolledOutAtBefore(rolloutCutoff);
        for (FeatureFlag f : permanentRollouts) {
            // Avoid duplicate reporting if already flagged
            boolean alreadyFlagged = reports.stream().anyMatch(r -> r.flagKey().equals(f.getKey()));
            if (!alreadyFlagged) {
                reports.add(new StaleFlagReport(
                    f.getId(),
                    f.getProject().getKey(),
                    f.getKey(),
                    f.getName(),
                    f.getOwner(),
                    f.getLastEvaluatedAt(),
                    f.getFullyRolledOutAt(),
                    "Permanent: Flag has been 100% rolled out for more than 14 days.",
                    "Recommend removing flag conditionals and cleaning up technical debt in codebase."
                ));
            }
        }

        return reports;
    }

    @Scheduled(fixedDelay = 3600_000) // Hourly scan
    public void runScheduledScan() {
        List<StaleFlagReport> stale = scanForStaleFlags();
        if (!stale.isEmpty()) {
            log.info("Flag Hygiene Scanner detected {} stale flags requiring cleanup", stale.size());
        }
    }
}
