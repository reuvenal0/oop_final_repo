import org.junit.jupiter.api.Test;

import java.util.List;
import org.view.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core tests for org.view that do NOT depend on org.model or org.io.
 * These tests should compile and run immediately.
 */
class ViewCoreTest {

    // -------------------------
    // ViewMode2D / ViewMode3D
    // -------------------------

    @Test
    void viewMode2D_basic() {
        ViewMode2D m = new ViewMode2D(0, 2);
        assertEquals(2, m.viewDim());
        assertEquals(0, m.axisIndex(0));
        assertEquals(2, m.axisIndex(1));
        assertThrows(IllegalArgumentException.class, () -> m.axisIndex(2));
    }

    @Test
    void viewMode2D_rejectsNegativeAndDuplicate() {
        assertThrows(IllegalArgumentException.class, () -> new ViewMode2D(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ViewMode2D(1, -3));
        assertThrows(IllegalArgumentException.class, () -> new ViewMode2D(5, 5));
    }

    @Test
    void viewMode3D_basic() {
        ViewMode3D m = new ViewMode3D(2, 1, 0);
        assertEquals(3, m.viewDim());
        assertEquals(2, m.axisIndex(0));
        assertEquals(1, m.axisIndex(1));
        assertEquals(0, m.axisIndex(2));
        assertThrows(IllegalArgumentException.class, () -> m.axisIndex(3));
    }

    @Test
    void viewMode3D_rejectsNegativeAndDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(0, 1, -1));

        assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(2, 1, 1));
    }

    // -------------------------
    // Point_2D / Point_3D / ViewPoint
    // -------------------------

    @Test
    void point2D_holdsCoordinates() {
        Point_2D p = new Point_2D(1.5, -2.0);
        assertEquals(1.5, p.x(), 1e-9);
        assertEquals(-2.0, p.y(), 1e-9);
    }

    @Test
    void point3D_holdsCoordinates() {
        Point_3D p = new Point_3D(1.0, 2.0, 3.0);
        assertEquals(1.0, p.x(), 1e-9);
        assertEquals(2.0, p.y(), 1e-9);
        assertEquals(3.0, p.z(), 1e-9);
    }

    // -------------------------
    // LabeledPoint + PointCloud
    // -------------------------

    @Test
    void labeledPoint_holdsIdAndPoint() {
        Point_2D p = new Point_2D(7, 8);
        LabeledPoint<String> lp = new LabeledPoint<>("king", p);

        assertEquals("king", lp.id());
        assertSame(p, lp.point());
    }

    @Test
    void pointCloud_holdsModeAndList() {
        ViewMode mode = new ViewMode2D(0, 1);

        LabeledPoint<String> a = new LabeledPoint<>("a", new Point_2D(1, 2));
        LabeledPoint<String> b = new LabeledPoint<>("b", new Point_2D(3, 4));

        PointCloud<String> cloud = new PointCloud<>(List.of(a, b), mode);

        assertEquals(mode, cloud.mode());
        assertEquals(2, cloud.points().size());
        assertEquals("a", cloud.points().get(0).id());
        assertEquals("b", cloud.points().get(1).id());
    }
}
