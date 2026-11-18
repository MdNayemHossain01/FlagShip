package io.flagship.core.engine;

import io.flagship.core.bucketing.BucketingStrategy;
import io.flagship.core.bucketing.MurmurHash3Bucketer;
import io.flagship.core.context.EvaluationContext;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.rules.FastRuleEvaluationVisitor;
import io.flagship.core.rules.RuleVisitor;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlagEvaluator {

    public enum MatchReason { KILL_SWITCH, INDIVIDUAL_TARGET, SEGMENT_RULE, PERCENTAGE_ROLLOUT, DEFAULT, FLAG_NOT_FOUND }

    public record EvaluationResult<T>(String flagKey, String variationName, T value, MatchReason reason, String ruleId) {}

    private final List<EvaluationStep> chain;
    private final BucketingStrategy bucketer;
    private final RuleVisitor ruleVisitor = new FastRuleEvaluationVisitor();

    public FlagEvaluator() {
        this(new MurmurHash3Bucketer());
    }

    public FlagEvaluator(BucketingStrategy bucketer) {
        this.bucketer = bucketer;
        this.chain = List.of(
            this::checkKillSwitch,
            this::checkIndividualTargets,
            this::checkSegmentRules,
            this::checkPercentageRollout,
            this::checkDefault
        );
    }

    @SuppressWarnings("unchecked")
    public <T> EvaluationResult<T> evaluate(FlagDefinition flag, EvaluationContext context, T codeFallback) {
        if (flag == null) {
            return new EvaluationResult<>("unknown", "code-fallback", codeFallback, MatchReason.FLAG_NOT_FOUND, null);
        }
        for (EvaluationStep step : chain) {
            StepMatch match = step.apply(flag, context);
            if (match != null) {
                Object rawValue = flag.variations().getOrDefault(match.variation(), codeFallback);
                return new EvaluationResult<>(flag.key(), match.variation(), (T) rawValue, match.reason(), match.ruleId());
            }
        }
        return new EvaluationResult<>(flag.key(), flag.defaultVariation(), codeFallback, MatchReason.DEFAULT, null);
    }

    @FunctionalInterface
    private interface EvaluationStep {
        StepMatch apply(FlagDefinition flag, EvaluationContext context);
    }

    private record StepMatch(String variation, MatchReason reason, String ruleId) {}

    private StepMatch checkKillSwitch(FlagDefinition flag, EvaluationContext ctx) {
        if (!flag.enabled()) {
            return new StepMatch(flag.offVariation(), MatchReason.KILL_SWITCH, null);
        }
        return null;
    }

    private StepMatch checkIndividualTargets(FlagDefinition flag, EvaluationContext ctx) {
        if (flag.individualTargets() == null) return null;
        for (Map.Entry<String, Set<String>> entry : flag.individualTargets().entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(ctx.getUserKey())) {
                return new StepMatch(entry.getKey(), MatchReason.INDIVIDUAL_TARGET, null);
            }
        }
        return null;
    }

    private StepMatch checkSegmentRules(FlagDefinition flag, EvaluationContext ctx) {
        if (flag.rules() == null) return null;
        for (FlagDefinition.SegmentRule rule : flag.rules()) {
            if (rule.rootNode() != null && rule.rootNode().accept(ruleVisitor, ctx)) {
                String variation = rule.variation() != null
                    ? rule.variation()
                    : resolveRolloutVariation(flag.key(), ctx.getUserKey(), flag.salt(), rule.rollout(), flag.defaultVariation());
                return new StepMatch(variation, MatchReason.SEGMENT_RULE, rule.id());
            }
        }
        return null;
    }

    private StepMatch checkPercentageRollout(FlagDefinition flag, EvaluationContext ctx) {
        if (flag.percentageRollout() == null || flag.percentageRollout().buckets() == null || flag.percentageRollout().buckets().isEmpty()) {
            return null;
        }
        String variation = resolveRolloutVariation(
            flag.key(), ctx.getUserKey(), flag.salt(), flag.percentageRollout(), flag.defaultVariation()
        );
        return new StepMatch(variation, MatchReason.PERCENTAGE_ROLLOUT, null);
    }

    private StepMatch checkDefault(FlagDefinition flag, EvaluationContext ctx) {
        return new StepMatch(flag.defaultVariation(), MatchReason.DEFAULT, null);
    }

    private String resolveRolloutVariation(
            String flagKey, String userKey, String salt, FlagDefinition.RolloutConfig rollout, String fallback) {
        if (rollout == null || rollout.buckets() == null || rollout.buckets().isEmpty()) return fallback;
        int bucket = bucketer.computeBucket(flagKey, userKey, salt);
        int cumulative = 0;
        for (FlagDefinition.WeightedVariation wv : rollout.buckets()) {
            cumulative += wv.weight();
            if (bucket < cumulative) {
                return wv.variation();
            }
        }
        return fallback;
    }
}
