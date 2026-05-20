import net.jqwik.api.ForAll
import net.jqwik.api.Property
import org.junit.jupiter.api.Assertions

// A property-based test that MUST hold for every method annotated
// @Associative and @Reducer in the codebase. Note: there is no logic
// here that is specific to addition — the test is the universal law
// written once. An AI agent (or a small bytecode-walking script) can
// derive one of these per @Associative method by reading the annotation;
// the body of the annotated method is not consulted.

class MonoidLawsTest {

    @Property
    boolean addIsAssociative(@ForAll int a, @ForAll int b, @ForAll int c) {
        Sum.add(Sum.add(a, b), c) == Sum.add(a, Sum.add(b, c))
    }

    @Property
    boolean addHasZero(@ForAll int a) {
        // @Reducer(zero = '0')
        Sum.add(a, 0) == a && Sum.add(0, a) == a
    }

    @Property
    boolean tallyIsAssociative(@ForAll Map<String, Integer> a,
                               @ForAll Map<String, Integer> b,
                               @ForAll Map<String, Integer> c) {
        Tally.merge(Tally.merge(a, b), c) == Tally.merge(a, Tally.merge(b, c))
    }
}
