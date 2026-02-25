package org.view;

import java.util.List;

public record PointCloud<T>(List<LabeledPoint<T>> points, ViewMode mode) { }