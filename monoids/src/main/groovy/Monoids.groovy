import groovy.transform.Associative
import groovy.transform.Reducer
import groovy.transform.TypeChecked

// A semigroup is "associative combine".
// A monoid adds an identity element ("zero").
// In Haskell:           class Semigroup a where <> :: a -> a -> a
//                       class Semigroup a => Monoid a where mempty :: a
// In functionaljava:    fj.Semigroup<A>, fj.Monoid<A> — built by combinator
// In highj:             org.highj.algebra.monoid.Monoid<A>
// In Groovy 6:          @Associative annotates the binary op;
//                       @Reducer adds an identity via zero();
//                       CombinerChecker enforces the contract at compile time.

class Sum {
    @Associative
    @Reducer(zero = '0')
    static int add(int a, int b) { a + b }
}

class Concat {
    @Associative
    @Reducer(zero = '""')
    static String join(String a, String b) { a + b }
}

// A bag-of-counts forms a monoid under per-key addition.
// Same algebra as Haskell's `Map k (Sum Int)` Monoid instance,
// or functionaljava's `Monoid.mapMonoid(Monoid.intAdditionMonoid)`.
class Tally {
    @Associative
    @Reducer(zero = '[:]')
    static Map<String, Integer> merge(Map<String, Integer> a, Map<String, Integer> b) {
        var out = new LinkedHashMap(a)
        b.each { k, v -> out.merge(k, v) { x, y -> x + y } }
        out
    }
}

@TypeChecked(extensions = 'groovy.typecheckers.CombinerChecker')
def reductions() {
    assert (1..100).toList().injectParallel(0, Sum.&add) == 5050
    assert ['a', 'b', 'c'].sumParallel(Concat.&join) == 'abc'

    def words = 'the quick brown fox jumps over the lazy dog'.split(/\s+/)
    var counts = words.collect { [(it): 1] as Map<String, Integer> }
                      .injectParallel([:], Tally.&merge)
    assert counts.values().sum() == 9
    assert counts['the'] == 2

    // The next line would be REJECTED at compile time by CombinerChecker
    // because subtraction is not associative:
    //   [1, 2, 3].injectParallel(0) { a, b -> a - b }
}

reductions()
println 'Monoid demo OK.'
