package org.view;

public record ViewMode3D(int xIndex, int yIndex, int zIndex) implements ViewMode {

    public ViewMode3D {
        if (xIndex < 0 || yIndex < 0 || zIndex < 0) {
            throw new IllegalArgumentException("Axis indices must be >= 0");
        }
        if (xIndex == yIndex || xIndex == zIndex || yIndex == zIndex) {
            throw new IllegalArgumentException("All axis indices must be different");
        }
    }

    @Override
    public int viewDim() {
        return 3;
    }

    @Override
    public int axisIndex(int axis) {
        return switch (axis) {
            case 0 -> xIndex;
            case 1 -> yIndex;
            case 2 -> zIndex;
            default -> throw new IllegalArgumentException("3D has no axis: " + axis);
        };
    }
}