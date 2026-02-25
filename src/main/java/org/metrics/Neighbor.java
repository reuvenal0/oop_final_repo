package org.metrics;

/**
 * A simple immutable result: an ID with its distance to the query.
 * Smaller distance means closer neighbor.
 */
public record Neighbor<T>(T id, double distance) {
}