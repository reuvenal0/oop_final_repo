package org.app.api.dto;

/** UI-safe projection score DTO. */
public record ProjectionScoreView<T>(T id, double coordinate, double orthogonalDistance, double purity) {
}
