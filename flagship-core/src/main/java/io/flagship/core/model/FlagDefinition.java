package io.flagship.core.model;

import io.flagship.core.rules.RuleNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record FlagDefinition(
    String key,
    FlagType type,
    boolean enabled,
    String offVariation,
    String defaultVariation,
    Map<String, Object> variations,
    Map<String, Set<String>> individualTargets,
    List<SegmentRule> rules,
    RolloutConfig percentageRollout,
    String salt
) {
    public record SegmentRule(String id, String name, RuleNode rootNode, String variation, RolloutConfig rollout) {}
    public record RolloutConfig(List<WeightedVariation> buckets) {}
    public record WeightedVariation(String variation, int weight) {}
}
