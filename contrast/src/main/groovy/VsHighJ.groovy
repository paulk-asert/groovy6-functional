import org.highj.data.Maybe
import org.highj.typeclass1.monad.Monad
import groovy.transform.TypeChecked
import static org.apache.groovy.macrolib.MacroLibGroovyMethods.DO

// highj simulates higher-kinded types on the JVM via the "lightweight
// HKT" encoding. That gives it real typeclasses — Functor, Applicative,
// Monad — at the cost of considerable boilerplate at every call site.
//
// Compare:

// highj — explicit typeclass instance, witness-tagged values
Monad<Maybe.µ> mMonad = Maybe.monad
def res1 = mMonad.bind(Maybe.Just(20),  a ->
           mMonad.bind(Maybe.Just(2),   b -> Maybe.Just(a / b)))
println res1   // Just(10)

// Groovy 6 — same algebra, no typeclass hierarchy and no µ tags
@TypeChecked(extensions = 'groovy.typecheckers.MonadicChecker')
Optional<Integer> divide(int x, int y) {
    DO(a in Optional.of(x),
       b in Optional.of(y)) {
        Optional.of((a / b).intValue())
    }
}
assert divide(20, 2).get() == 10

// Trade-off:
// * highj  — true HKT abstraction; one `do`-style helper works for all
//            carriers, but every value carries a witness tag.
// * Groovy — no HKT, no typeclasses; structural + allow-list +
//            @Monadic. You can DO across Optional, Stream, Awaitable,
//            CompletableFuture, fj.* and user types without inheritance.
// For most JVM teams the second trade is the one that ships.
println 'highj contrast OK.'
