package io.flagship.core.rules;

import io.flagship.core.context.EvaluationContext;

public interface RuleVisitor {
    boolean visitAnd(RuleNode.AndNode node, EvaluationContext ctx);
    boolean visitOr(RuleNode.OrNode node, EvaluationContext ctx);
    boolean visitLeaf(RuleNode.LeafClause clause, EvaluationContext ctx);
}
