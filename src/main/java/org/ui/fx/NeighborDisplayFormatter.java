package org.ui.fx;

import org.app.api.dto.NeighborView;

final class NeighborDisplayFormatter {

    private NeighborDisplayFormatter() {}

    static <T> String format(NeighborView<T> neighbor) {
        if (neighbor.displayLabel() != null && !neighbor.displayLabel().isBlank()) {
            return neighbor.displayLabel();
        }
        return String.format("%s   |   dist=%.5f", neighbor.id(), neighbor.distance());
    }
}
