package org.view;

/**
 * UI-controlled state: which axes are used for viewing
 */
public interface ViewMode {

    /**
     * @return i.e: 2 for 2D, 3 for 3D.
     */
    int viewDim();

    /**
     * i.e: axis=0 -> X, axis=1 -> Y, axis=2 -> Z
     */
    int axisIndex(int axis);
}
