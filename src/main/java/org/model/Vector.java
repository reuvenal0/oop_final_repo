package org.model;

import java.util.Arrays;
import java.util.Collection;

/**
 * An immutable mathematical vector of doubles
 */
public final class Vector {

    private final double[] data;

    /**
     * Constructs a main.org.model.Vector from the given array.
     * The input array is copied to keep immutability.
     *
     * @param values raw vector values (must be non-null and non-empty)
     */
    public Vector(double[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        this.data = Arrays.copyOf(values, values.length);
    }

    /**
     * @return the vector dimension (number of components).
     */
    public int dim() {
        return data.length;
    }

    /**
     * Returns a defensive copy of the internal data.
     * This prevents callers from mutating the main.org.model.Vector's state.
     */
    public double[] toArrayCopy() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Computes the dot product between this vector and another vector.
     *
     * @throws IllegalArgumentException if dimensions do not match.
     */
    public double dot(Vector other) {
        requireSameDim(other);
        double sum = 0.0;
        for (int i = 0; i < data.length; i++) {
            sum += this.data[i] * other.data[i];
        }
        return sum;
    }

    /**
     * Computes the L2 norm (Euclidean length).
     */
    public double norm() {
        // norm = sqrt(sum_i (x_i^2))
        double sumSq = 0.0;
        for (double v : data) {
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }

    /**
     * Returns an L2-normalized vector (length = 1).
     *
     * @throws IllegalStateException if the vector is a zero vector.
     */
    public Vector normalized() {
        double n = norm();
        if (n == 0.0) {
            throw new IllegalStateException("Cannot normalize a zero vector");
        }
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i] / n;
        }
        return new Vector(out);
    }

    /**
     * Adds another vector to this vector.
     */
    public Vector add(Vector other) {
        requireSameDim(other);
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = this.data[i] + other.data[i];
        }
        return new Vector(out);
    }

    /**
     * Subtracts another vector from this vector.
     */
    public Vector subtract(Vector other) {
        requireSameDim(other);
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = this.data[i] - other.data[i];
        }
        return new Vector(out);
    }

    /**
     * Scales this vector by a constant factor.
     */
    public Vector scale(double alpha) {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = alpha * this.data[i];
        }
        return new Vector(out);
    }

    /**
     * Help method: Ensures that another vector has the same dimension as this one.
     * @throws IllegalArgumentException if the other vector is null or has a different dimension
     */
    private void requireSameDim(Vector other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        if (this.data.length != other.data.length) {
            throw new IllegalArgumentException(
                    "Dimension mismatch: " + this.data.length + " vs " + other.data.length
            );
        }
    }

    /**
     * Returns the value at the given component index.
     *
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public double get(int index) {
        if (index < 0 || index >= data.length) {
            throw new IndexOutOfBoundsException("index=" + index + ", dim=" + data.length);
        }
        return data[index];
    }

    /**
     * Returns the squared L2 norm of the vector (||v||^2).
     * Useful when sqrt() is unnecessary.
     */
    public double normSquared() {
        double sumSq = 0.0;
        for (double v : data) {
            sumSq += v * v;
        }
        return sumSq;
    }

    public static Vector average(Collection<Vector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("Cannot average empty vectors");
        }

        int dim = vectors.iterator().next().dim();
        double[] sum = new double[dim];

        // Efficient accumulation without creating temporary objects
        for (Vector v : vectors) {
            if (v.dim() != dim) {
                throw new IllegalArgumentException(
                        "Cannot average vectors with different dimensions. Expected " + dim + " but got " + v.dim()
                );
            }
            for (int i = 0; i < dim; i++) sum[i] += v.get(i);
        }

        // divide the sum:
        int n = vectors.size();
        for (int i = 0; i < dim; i++) {
            sum[i] /= n;
        }

        return new Vector(sum);
    }

    @Override
    public String toString() {
        // Short, readable summary (avoid printing huge vectors fully)
        return "Vector(dim=" + data.length + ")";
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj.getClass() != this.getClass()) return false;

        Vector other = (Vector) obj;
        return Arrays.equals(this.data, other.data);
    }
}