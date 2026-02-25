package org.view;

public record ViewMode2D(int xIndex, int yIndex) implements ViewMode {

    public ViewMode2D {
        if (xIndex < 0 || yIndex < 0) {
            throw new IllegalArgumentException("Axis indices must be >= 0");
        }
        if (xIndex == yIndex) {
            throw new IllegalArgumentException("xIndex and yIndex must be different");
        }
    }

    @Override
    public int viewDim() {
        return 2;
    }

    @Override
    public int axisIndex(int axis) {
        return switch (axis) {
            case 0 -> xIndex;
            case 1 -> yIndex;
            default -> throw new IllegalArgumentException("2D has no axis: " + axis);
        };
    }
}