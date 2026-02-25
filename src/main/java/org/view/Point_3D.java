package org.view;

public record Point_3D(double x, double y, double z) implements ViewPoint {
    @Override public int dim() { return 3; }
}