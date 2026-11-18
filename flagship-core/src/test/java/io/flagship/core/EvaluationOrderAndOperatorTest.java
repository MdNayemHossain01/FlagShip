package io.flagship.core;

import io.flagship.core.context.EvaluationContext;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import io.flagship.core.rules.Operator;
import io.flagship.core.rules.RuleNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationOrderAndOperatorTest {

    private FlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FlagEvaluator();
    }

    @Test
    void testEvaluationOrder_KillSwitchTrumpsAll() {
        RuleNode.LeafClause rule = new RuleNode.LeafClause("country", Operator.EQUALS, List.of("US"));
        FlagDefinition flag = new FlagDefinition(
            "checkout-flag", FlagType.BOOLEAN, false, "killed-off", "default-var",
            Map.of("on", true, "killed-off", false),
            Map.of("on", Set.of("target-user")), // Target user would match
            List.of(new FlagDefinition.SegmentRule("r1", "rule1", rule, "on", null)),
            new FlagDefinition.RolloutConfig(List.of(new FlagDefinition.WeightedVariation("on", 100_000))),
            "v1"
        );

        EvaluationContext ctx = EvaluationContext.builder("target-user")
            .attribute("country", "US")
            .build();

        FlagEvaluator.EvaluationResult<?> result = evaluator.evaluate(flag, ctx, null);
        assertEquals(FlagEvaluator.MatchReason.KILL_SWITCH, result.reason());
        assertEquals("killed-off", result.variationName());
        assertEquals(false, result.value());
    }

    @Test
    void testEvaluationOrder_IndividualTargetTrumpsSegmentAndRollout() {
        RuleNode.LeafClause rule = new RuleNode.LeafClause("country", Operator.EQUALS, List.of("US"));
        FlagDefinition flag = new FlagDefinition(
            "checkout-flag", FlagType.BOOLEAN, true, "off", "default-var",
            Map.of("vip", "VIP_EXPERIENCE", "rule-var", "RULE_EXP", "default-var", "DEF"),
            Map.of("vip", Set.of("special-user")),
            List.of(new FlagDefinition.SegmentRule("r1", "rule1", rule, "rule-var", null)),
            null,
            "v1"
        );

        EvaluationContext ctx = EvaluationContext.builder("special-user")
            .attribute("country", "US")
            .build();

        FlagEvaluator.EvaluationResult<String> result = evaluator.evaluate(flag, ctx, "FALLBACK");
        assertEquals(FlagEvaluator.MatchReason.INDIVIDUAL_TARGET, result.reason());
        assertEquals("vip", result.variationName());
        assertEquals("VIP_EXPERIENCE", result.value());
    }

    @Test
    void testEvaluationOrder_SegmentRuleTrumpsPercentageRollout() {
        RuleNode.LeafClause rule = new RuleNode.LeafClause("plan", Operator.EQUALS, List.of("ENTERPRISE"));
        FlagDefinition flag = new FlagDefinition(
            "feature-flag", FlagType.STRING, true, "off", "default-val",
            Map.of("ent", "ENTERPRISE_TIER", "rollout-var", "ROLLOUT_TIER", "default-val", "DEFAULT_TIER"),
            Map.of(),
            List.of(new FlagDefinition.SegmentRule("r1", "Enterprise Rule", rule, "ent", null)),
            new FlagDefinition.RolloutConfig(List.of(new FlagDefinition.WeightedVariation("rollout-var", 100_000))),
            "v1"
        );

        EvaluationContext ctx = EvaluationContext.builder("user-enterprise")
            .attribute("plan", "ENTERPRISE")
            .build();

        FlagEvaluator.EvaluationResult<String> result = evaluator.evaluate(flag, ctx, "FALLBACK");
        assertEquals(FlagEvaluator.MatchReason.SEGMENT_RULE, result.reason());
        assertEquals("ent", result.variationName());
        assertEquals("ENTERPRISE_TIER", result.value());
    }

    @Test
    void testOperators_SemVerAndRegexAndDates() {
        // Semver Greater Than
        RuleNode.LeafClause semverGt = new RuleNode.LeafClause("appVersion", Operator.SEMVER_GREATER_THAN, List.of("2.5.0"));
        // Regex email
        RuleNode.LeafClause regexEmail = new RuleNode.LeafClause("email", Operator.REGEX, List.of(".*@company\\.com$"));
        // Date after
        RuleNode.LeafClause dateAfter = new RuleNode.LeafClause("signupDate", Operator.DATE_AFTER, List.of("2026-01-01T00:00:00Z"));

        RuleNode.AndNode andRoot = new RuleNode.AndNode(List.of(semverGt, regexEmail, dateAfter));

        FlagDefinition flag = new FlagDefinition(
            "advanced-rule-flag", FlagType.STRING, true, "off", "default-var",
            Map.of("matched", "ALL_PASSED", "off", "OFF", "default-var", "DEFAULT"),
            Map.of(),
            List.of(new FlagDefinition.SegmentRule("r-adv", "Advanced Composite", andRoot, "matched", null)),
            null,
            "v1"
        );

        // Matching context
        EvaluationContext matchCtx = EvaluationContext.builder("user-1")
            .attribute("appVersion", "2.6.1")
            .attribute("email", "engineer@company.com")
            .attribute("signupDate", Instant.parse("2026-06-15T12:00:00Z"))
            .build();

        FlagEvaluator.EvaluationResult<String> resMatch = evaluator.evaluate(flag, matchCtx, "FALLBACK");
        assertEquals("matched", resMatch.variationName());
        assertEquals("ALL_PASSED", resMatch.value());

        // Failing context (older version)
        EvaluationContext failCtx = EvaluationContext.builder("user-2")
            .attribute("appVersion", "2.4.9")
            .attribute("email", "engineer@company.com")
            .attribute("signupDate", Instant.parse("2026-06-15T12:00:00Z"))
            .build();

        FlagEvaluator.EvaluationResult<String> resFail = evaluator.evaluate(flag, failCtx, "FALLBACK");
        assertEquals(FlagEvaluator.MatchReason.DEFAULT, resFail.reason());
        assertEquals("default-var", resFail.variationName());
    }

    @Test
    void testOperators_InAndContains() {
        RuleNode.LeafClause inClause = new RuleNode.LeafClause("role", Operator.IN, List.of("ADMIN", "MANAGER", "LEAD"));
        RuleNode.LeafClause containsClause = new RuleNode.LeafClause("userAgent", Operator.CONTAINS, List.of("Mobile"));
        RuleNode.OrNode orRoot = new RuleNode.OrNode(List.of(inClause, containsClause));

        FlagDefinition flag = new FlagDefinition(
            "in-contains-flag", FlagType.BOOLEAN, true, "off", "off",
            Map.of("on", true, "off", false),
            Map.of(),
            List.of(new FlagDefinition.SegmentRule("r-or", "OR Rule", orRoot, "on", null)),
            null,
            "v1"
        );

        // Match via IN
        assertTrue(evaluator.evaluate(flag, EvaluationContext.builder("u1").attribute("role", "MANAGER").build(), false).value());
        // Match via CONTAINS
        assertTrue(evaluator.evaluate(flag, EvaluationContext.builder("u2").attribute("userAgent", "Mozilla/5.0 Mobile Safari").build(), false).value());
        // No match
        assertFalse(evaluator.evaluate(flag, EvaluationContext.builder("u3").attribute("role", "GUEST").attribute("userAgent", "Desktop").build(), false).value());
    }
}
