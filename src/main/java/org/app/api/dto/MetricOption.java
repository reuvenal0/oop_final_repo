package org.app.api.dto;

import java.util.Objects;

/** UI-safe descriptor for selectable distance metrics. */
public record MetricOption(String id, String label) {
    public MetricOption {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }

    @Override
    public String toString() {
        return label;
    }
}
