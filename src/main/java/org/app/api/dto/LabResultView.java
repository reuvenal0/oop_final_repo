package org.app.api.dto;

import java.util.List;
import java.util.Objects;

/** UI-safe vector arithmetic result. */
public record LabResultView<T>(List<NeighborView<T>> neighbors) {
    public LabResultView {
        Objects.requireNonNull(neighbors, "neighbors must not be null");
        neighbors = List.copyOf(neighbors);
    }
}
