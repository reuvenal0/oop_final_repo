package org.projection;

/**
 * Projection result for one ID:
 * - coordinate: position along the axis (t)
 * - orthogonalDistance: distance from axis (smaller = more "on the scale")
 * - purity: how strongly the word belongs to the axis direction relative to deviation
 */
public record ProjectionScore<T>(T id, double coordinate, double orthogonalDistance, double purity) {
}