package org.view;

public record Point_2D(double x, double y) implements ViewPoint {
    @Override public int dim() { return 2; }
}
