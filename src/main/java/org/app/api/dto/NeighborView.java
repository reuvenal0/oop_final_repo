package org.app.api.dto;

/** UI-safe nearest-neighbor DTO (no dependency on metrics package). */
public record NeighborView<T>(T id, double distance, String displayLabel) {
}
