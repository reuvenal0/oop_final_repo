package org.lab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A vector expression is a list of signed terms: (+a) + (-b) + (+c) ...
 */
public final class VectorExpression<T> {

    private final List<Term<T>> terms;

    public VectorExpression(List<Term<T>> terms) {
        Objects.requireNonNull(terms, "terms must not be null");
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("terms must not be empty");
        }
        this.terms = List.copyOf(terms);
    }

    public List<Term<T>> terms() {
        return terms;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private final List<Term<T>> terms = new ArrayList<>();

        public Builder<T> plus(T id) {
            terms.add(Term.plus(id));
            return this;
        }

        public Builder<T> minus(T id) {
            terms.add(Term.minus(id));
            return this;
        }

        public VectorExpression<T> build() {
            return new VectorExpression<>(terms);
        }
    }
}