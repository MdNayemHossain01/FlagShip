package io.flagship.core.rules;

import io.flagship.core.context.EvaluationContext;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public final class FastRuleEvaluationVisitor implements RuleVisitor {

    @Override
    public boolean visitAnd(RuleNode.AndNode node, EvaluationContext ctx) {
        if (node.children() == null) return true;
        for (RuleNode child : node.children()) {
            if (!child.accept(this, ctx)) return false;
        }
        return true;
    }

    @Override
    public boolean visitOr(RuleNode.OrNode node, EvaluationContext ctx) {
        if (node.children() == null) return false;
        for (RuleNode child : node.children()) {
            if (child.accept(this, ctx)) return true;
        }
        return false;
    }

    @Override
    public boolean visitLeaf(RuleNode.LeafClause clause, EvaluationContext ctx) {
        Object rawAttr = "userKey".equals(clause.attribute()) ? ctx.getUserKey() : ctx.getAttribute(clause.attribute());
        if (rawAttr == null || clause.values() == null || clause.values().isEmpty()) return false;
        String actual = String.valueOf(rawAttr);
        String target = clause.values().getFirst();

        return switch (clause.operator()) {
            case EQUALS -> actual.equals(target);
            case NOT_EQUALS -> !actual.equals(target);
            case IN -> clause.values().contains(actual);
            case CONTAINS -> actual.contains(target);
            case REGEX -> Pattern.compile(target).matcher(actual).matches();
            case SEMVER_EQ -> compareSemVer(actual, target) == 0;
            case SEMVER_GT, SEMVER_GREATER_THAN -> compareSemVer(actual, target) > 0;
            case SEMVER_LT, SEMVER_LESS_THAN -> compareSemVer(actual, target) < 0;
            case DATE_BEFORE -> parseInstant(actual).isBefore(parseInstant(target));
            case DATE_AFTER -> parseInstant(actual).isAfter(parseInstant(target));
        };
    }

    public static int compareSemVer(String v1, String v2) {
        String clean1 = v1.replaceAll("^v", "").trim();
        String clean2 = v2.replaceAll("^v", "").trim();
        String[] p1 = clean1.split("\\.");
        String[] p2 = clean2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? parseIntSafe(p1[i]) : 0;
            int n2 = i < p2.length ? parseIntSafe(p2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Instant parseInstant(String s) {
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            try {
                // Support yyyy-MM-dd formatted dates
                return Instant.parse(s + "T00:00:00Z");
            } catch (Exception ex) {
                return Instant.EPOCH;
            }
        }
    }
}
