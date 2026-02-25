package org.lab;

import org.io.Representation;
import org.model.EmbeddingGroup;
import org.model.Vector;

import java.util.Objects;

/**
 * Evaluates a VectorExpression into a single Vector in a specific representation (usually FULL).
 */
public final class VectorExpressionEvaluator<T> {

    private final EmbeddingGroup<T> group;
    private final Representation representation;

    public VectorExpressionEvaluator(EmbeddingGroup<T> group, Representation representation) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.representation = Objects.requireNonNull(representation, "representation must not be null");
    }

    public Vector evaluate(VectorExpression<T> expr) {
        Objects.requireNonNull(expr, "expr must not be null");

        // Start from the first term's vector, then add/subtract the rest.
        Term<T> first = expr.terms().get(0);
        Vector acc = group.require(first.id(), representation);
        if (first.sign() == -1) {
            acc = acc.scale(-1.0);
        }

        for (int i = 1; i < expr.terms().size(); i++) {
            Term<T> term = expr.terms().get(i);
            Vector v = group.require(term.id(), representation);
            acc = (term.sign() == 1) ? acc.add(v) : acc.subtract(v);
        }

        return acc;
    }
}