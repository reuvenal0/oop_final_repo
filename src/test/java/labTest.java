import org.io.Representation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lab.*;
import org.model.EmbeddingGroup;
import org.model.*;
import org.model.Vector;
import org.metrics.DistanceMetric;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the org.lab package:
 * - Term
 * - VectorExpression (+ Builder)
 * - VectorExpressionEvaluator
 * - LabResult
 * - VectorArithmeticLab (validation + smoke)
 *
 * Notes:
 * - This file focuses on what we can test deterministically without knowing the internal
 *   constructors/builders of EmbeddingGroup or NearestNeighbors.
 */
class latTest {
    // ----------------------------
    // Term tests
    // ----------------------------
    @Nested
    class TermTests {

        @Test
        void ctor_validPlus_ok() {
            Term<String> t = new Term<>("a", 1);
            assertEquals("a", t.id());
            assertEquals(1, t.sign());
        }

        @Test
        void ctor_validMinus_ok() {
            Term<String> t = new Term<>("a", -1);
            assertEquals("a", t.id());
            assertEquals(-1, t.sign());
        }

        @Test
        void ctor_nullId_throws() {
            assertThrows(NullPointerException.class, () -> new Term<>(null, 1));
        }

        @Test
        void ctor_invalidSign_throws() {
            assertThrows(IllegalArgumentException.class, () -> new Term<>("a", 0));
            assertThrows(IllegalArgumentException.class, () -> new Term<>("a", 2));
            assertThrows(IllegalArgumentException.class, () -> new Term<>("a", -2));
        }

        @Test
        void plusFactory_createsPlusTerm() {
            Term<String> t = Term.plus("x");
            assertEquals("x", t.id());
            assertEquals(1, t.sign());
        }

        @Test
        void minusFactory_createsMinusTerm() {
            Term<String> t = Term.minus("x");
            assertEquals("x", t.id());
            assertEquals(-1, t.sign());
        }
    }

    // ----------------------------
    // VectorExpression tests
    // ----------------------------
    @Nested
    class VectorExpressionTests {

        @Test
        void ctor_nullTerms_throws() {
            assertThrows(NullPointerException.class, () -> new VectorExpression<String>(null));
        }

        @Test
        void ctor_emptyTerms_throws() {
            assertThrows(IllegalArgumentException.class, () -> new VectorExpression<String>(List.of()));
        }

        @Test
        void ctor_copiesList_defensiveCopy() {
            List<Term<String>> original = new ArrayList<>();
            original.add(Term.plus("a"));

            VectorExpression<String> expr = new VectorExpression<>(original);

            // Mutate original list; expression should not change
            original.add(Term.plus("b"));
            assertEquals(1, expr.terms().size(),
                    "VectorExpression should copy the terms list defensively");
        }

        @Test
        void termsList_isUnmodifiable() {
            VectorExpression<String> expr = new VectorExpression<>(List.of(Term.plus("a")));
            assertThrows(UnsupportedOperationException.class, () -> expr.terms().add(Term.plus("b")));
        }

        @Test
        void builder_buildsCorrectOrderAndSigns() {
            VectorExpression<String> expr = VectorExpression.<String>builder()
                    .plus("a")
                    .minus("b")
                    .plus("c")
                    .build();

            assertEquals(3, expr.terms().size());

            assertEquals("a", expr.terms().get(0).id());
            assertEquals(1, expr.terms().get(0).sign());

            assertEquals("b", expr.terms().get(1).id());
            assertEquals(-1, expr.terms().get(1).sign());

            assertEquals("c", expr.terms().get(2).id());
            assertEquals(1, expr.terms().get(2).sign());
        }
    }

    // ----------------------------
    // VectorExpressionEvaluator tests
    // ----------------------------
    @Nested
    class VectorExpressionEvaluatorTests {

        /**
         * Minimal fake EmbeddingGroup that supports the full interface contract,
         * but only "require(id, rep)" is actually used by VectorExpressionEvaluator.
         */
        private static class FakeEmbeddingGroup implements EmbeddingGroup<String> {
            private final Map<String, Vector> data;
            private final Representation rep;

            FakeEmbeddingGroup(Map<String, Vector> data, Representation rep) {
                this.data = Map.copyOf(data);
                this.rep = Objects.requireNonNull(rep, "rep must not be null");
            }

            @Override
            public boolean contains(String id) {
                return data.containsKey(id);
            }

            @Override
            public Optional<EmbeddingSingle<String>> find(String id) {
                // Not needed for these tests
                return Optional.empty();
            }

            @Override
            public EmbeddingSingle<String> requireSingle(String id) {
                // Not needed for these tests
                throw new UnsupportedOperationException("requireSingle() is not used in evaluator tests");
            }

            @Override
            public Vector require(String id, Representation rep) {
                Vector v = data.get(id);
                if (v == null) throw new IllegalArgumentException("Unknown id: " + id);
                return v;
            }

            @Override
            public Set<String> ids() {
                return data.keySet();
            }

            @Override
            public Set<Representation> availableRepresentations() {
                return Set.of(rep);
            }
        }

        @Test
        void ctor_nullGroup_throws() {
            assertThrows(NullPointerException.class,
                    () -> new VectorExpressionEvaluator<String>(null, Representation.of("FULL")));
        }

        @Test
        void ctor_nullRepresentation_throws() {
            EmbeddingGroup<String> group = new FakeEmbeddingGroup(
                    Map.of("a", new Vector(new double[]{1.0, 2.0})),
                    Representation.of("FULL")
            );
            assertThrows(NullPointerException.class, () -> new VectorExpressionEvaluator<>(group, null));
        }

        @Test
        void evaluate_nullExpr_throws() {
            EmbeddingGroup<String> group = new FakeEmbeddingGroup(
                    Map.of("a", new Vector(new double[]{1.0, 2.0})),
                    Representation.of("FULL")
            );
            VectorExpressionEvaluator<String> eval = new VectorExpressionEvaluator<>(group, Representation.of("FULL"));
            assertThrows(NullPointerException.class, () -> eval.evaluate(null));
        }

        @Test
        void evaluate_singlePlusTerm_returnsThatVector() {
            Representation rep = Representation.of("FULL");
            EmbeddingGroup<String> group = new FakeEmbeddingGroup(
                    Map.of("a", new Vector(new double[]{1.0, 2.0})),
                    rep
            );
            VectorExpressionEvaluator<String> eval = new VectorExpressionEvaluator<>(group, rep);

            VectorExpression<String> expr = VectorExpression.<String>builder().plus("a").build();
            Vector out = eval.evaluate(expr);

            assertVectorEquals(new double[]{1.0, 2.0}, out);
        }

        @Test
        void evaluate_singleMinusTerm_scalesByMinusOne() {
            Representation rep = Representation.of("FULL");
            EmbeddingGroup<String> group = new FakeEmbeddingGroup(
                    Map.of("a", new Vector(new double[]{1.0, 2.0})),
                    rep
            );
            VectorExpressionEvaluator<String> eval = new VectorExpressionEvaluator<>(group, rep);

            VectorExpression<String> expr = new VectorExpression<>(List.of(Term.minus("a")));
            Vector out = eval.evaluate(expr);

            assertVectorEquals(new double[]{-1.0, -2.0}, out);
        }

        @Test
        void evaluate_multipleTerms_addAndSubtract() {
            Representation rep = Representation.of("FULL");
            EmbeddingGroup<String> group = new FakeEmbeddingGroup(
                    Map.of(
                            "a", new Vector(new double[]{10.0, 1.0}),
                            "b", new Vector(new double[]{2.0, 3.0}),
                            "c", new Vector(new double[]{4.0, 5.0})
                    ),
                    rep
            );
            VectorExpressionEvaluator<String> eval = new VectorExpressionEvaluator<>(group, rep);

            // a - b + c = (10,1) - (2,3) + (4,5) = (12,3)
            VectorExpression<String> expr = VectorExpression.<String>builder()
                    .plus("a")
                    .minus("b")
                    .plus("c")
                    .build();

            Vector out = eval.evaluate(expr);
            assertVectorEquals(new double[]{12.0, 3.0}, out);
        }

        private void assertVectorEquals(double[] expected, Vector actual) {
            Vector expectedVec = new Vector(expected);

            // If Vector implements equals(), great. If not, tell me Vector's API (get(i)/toArray())
            // and I'll switch to element-wise assertions.
            assertEquals(expectedVec, actual,
                    "Vector values differ. If Vector doesn't implement equals(), share Vector API and I'll adapt.");
        }
    }

    // ----------------------------
    // LabResult tests
    // ----------------------------
    @Nested
    class LabResultTests {

        @Test
        void ctor_nullVector_throws() {
            assertThrows(NullPointerException.class, () -> new LabResult<String>(null, List.of()));
        }

        @Test
        void ctor_nullNeighbors_throws() {
            assertThrows(NullPointerException.class,
                    () -> new LabResult<String>(new Vector(new double[]{1.0}), null));
        }
    }

    // ----------------------------
    // VectorArithmeticLab tests
    // ----------------------------
    @Nested
    class VectorArithmeticLabTests {

        @Test
        void ctor_nullGroup_throws() {
            assertThrows(NullPointerException.class,
                    () -> new VectorArithmeticLab<String>(null, Representation.of("FULL"), dummyMetric()));
        }

        @Test
        void ctor_nullRepresentation_throws() {
            EmbeddingGroup<String> group = dummyGroupForCtorOnly();
            assertThrows(NullPointerException.class,
                    () -> new VectorArithmeticLab<>(group, null, dummyMetric()));
        }

        @Test
        void ctor_nullMetric_throws() {
            EmbeddingGroup<String> group = dummyGroupForCtorOnly();
            assertThrows(NullPointerException.class,
                    () -> new VectorArithmeticLab<>(group, Representation.of("FULL"), null));
        }

        @Test
        void solve_nullExpr_throws() {
            EmbeddingGroup<String> group = dummyGroupForCtorOnly();
            VectorArithmeticLab<String> lab = new VectorArithmeticLab<>(group, Representation.of("FULL"), dummyMetric());
            assertThrows(NullPointerException.class, () -> lab.solve(null, 1));
        }

        @Test
        void solve_kMustBePositive_throws() {
            EmbeddingGroup<String> group = dummyGroupForCtorOnly();
            VectorArithmeticLab<String> lab = new VectorArithmeticLab<>(group, Representation.of("FULL"), dummyMetric());

            VectorExpression<String> expr = VectorExpression.<String>builder().plus("a").build();

            assertThrows(IllegalArgumentException.class, () -> lab.solve(expr, 0));
            assertThrows(IllegalArgumentException.class, () -> lab.solve(expr, -5));
        }

        /**
         * Dummy group for constructor/validation tests only.
         * We implement the full interface but keep behavior minimal.
         */
        private EmbeddingGroup<String> dummyGroupForCtorOnly() {
            Representation rep = Representation.of("FULL");

            return new EmbeddingGroup<String>() {
                @Override
                public boolean contains(String id) {
                    return true;
                }

                @Override
                public Optional<EmbeddingSingle<String>> find(String id) {
                    return Optional.empty();
                }

                @Override
                public EmbeddingSingle<String> requireSingle(String id) {
                    throw new UnsupportedOperationException("Not needed for these tests");
                }

                @Override
                public Vector require(String id, Representation rep) {
                    return new Vector(new double[]{1.0, 1.0});
                }

                @Override
                public Set<String> ids() {
                    return Set.of("a", "b");
                }

                @Override
                public Set<Representation> availableRepresentations() {
                    return Set.of(rep);
                }
            };
        }

        /**
         * Dummy metric to satisfy the constructor.
         */
        private DistanceMetric dummyMetric() {
            return new DistanceMetric() {
                @Override
                public double distance(Vector a, Vector b) {
                    return 0.0;
                }

                @Override
                public String name() {
                    return "dummy";
                }
            };
        }
    }
}