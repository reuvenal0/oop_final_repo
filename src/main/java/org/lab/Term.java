package org.lab;

import java.util.Objects;

/**
 * One signed term in a vector expression: +id or -id.
 */
public record Term<T>(T id, int sign) {

    public Term {
        Objects.requireNonNull(id, "id must not be null");
        if (sign != 1 && sign != -1) {
            throw new IllegalArgumentException("sign must be +1 or -1");
        }
    }

    public static <T> Term<T> plus(T id) {
        return new Term<>(id, 1);
    }

    public static <T> Term<T> minus(T id) {
        return new Term<>(id, -1);
    }
}