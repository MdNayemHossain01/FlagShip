package io.flagship.core.rules;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.flagship.core.context.EvaluationContext;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "nodeType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RuleNode.AndNode.class, name = "AND"),
    @JsonSubTypes.Type(value = RuleNode.OrNode.class, name = "OR"),
    @JsonSubTypes.Type(value = RuleNode.LeafClause.class, name = "LEAF")
})
public sealed interface RuleNode permits RuleNode.AndNode, RuleNode.OrNode, RuleNode.LeafClause {
    boolean accept(RuleVisitor visitor, EvaluationContext context);

    record AndNode(List<RuleNode> children) implements RuleNode {
        @Override public boolean accept(RuleVisitor visitor, EvaluationContext ctx) { return visitor.visitAnd(this, ctx); }
    }

    record OrNode(List<RuleNode> children) implements RuleNode {
        @Override public boolean accept(RuleVisitor visitor, EvaluationContext ctx) { return visitor.visitOr(this, ctx); }
    }

    record LeafClause(String attribute, Operator operator, List<String> values) implements RuleNode {
        @Override public boolean accept(RuleVisitor visitor, EvaluationContext ctx) { return visitor.visitLeaf(this, ctx); }
        public Set<String> fastLookupSet() { return values == null ? Set.of() : Set.copyOf(values); }
        public Pattern compiledPattern() { return values != null && !values.isEmpty() ? Pattern.compile(values.getFirst()) : null; }
    }
}
