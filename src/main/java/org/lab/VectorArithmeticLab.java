package org.lab;

import org.io.Representation;
import org.metrics.DistanceMetric;
import org.metrics.NearestNeighbors;
import org.metrics.Neighbor;
import org.model.EmbeddingGroup;
import org.model.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Backend "lab" API for vector arithmetic.
 * UI calls this class; it hides evaluation + nearest-neighbor logic.
 */
public final class VectorArithmeticLab<T> {

    private final VectorExpressionEvaluator<T> evaluator;
    private final NearestNeighbors<T> neighbors;

    public VectorArithmeticLab(EmbeddingGroup<T> group, Representation rep, DistanceMetric metric) {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(rep, "rep must not be null");
        Objects.requireNonNull(metric, "metric must not be null");

        this.evaluator = new VectorExpressionEvaluator<>(group, rep);
        this.neighbors = new NearestNeighbors<>(group, rep, metric);
    }

    /**
     * Evaluates the expression and returns topK nearest neighbors to the result vector.
     * Automatically excludes all IDs that appear in the expression (to avoid trivial answers).
     */
    public LabResult<T> solve(VectorExpression<T> expr, int k) {
        Objects.requireNonNull(expr, "expr must not be null");
        if (k <= 0) throw new IllegalArgumentException("k must be >= 1");

        Vector result = evaluator.evaluate(expr);

        Set<T> exclude = new HashSet<>();
        for (Term<T> term : expr.terms()) {
            exclude.add(term.id());
        }

        List<Neighbor<T>> top = neighbors.topK(result, k, exclude);
        return new LabResult<>(result, top);
    }
}