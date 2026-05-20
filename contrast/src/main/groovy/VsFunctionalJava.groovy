import fj.F
import fj.data.List as FJList
import fj.data.Option
import fj.data.Validation
import groovy.transform.TypeChecked
import static org.apache.groovy.macrolib.MacroLibGroovyMethods.DO

// FunctionalJava is still useful — and Groovy 6 doesn't fight it.
// The DO macro's standard allow-list recognises fj.data.Option,
// fj.data.List, fj.data.Stream and fj.data.Validation BY NAME,
// without a Groovy dependency on FunctionalJava.

// 1. FJ style — explicit binds via fj.F
F<Integer, Option<Integer>> halveIfEven = i -> i % 2 == 0 ? Option.some(i / 2 as int) : Option.none()
Option<Integer> result = Option.some(20).bind(halveIfEven).bind(halveIfEven)
assert result == Option.some(5)

// 2. Same thing, via DO — the carrier is still fj.data.Option
@TypeChecked(extensions = 'groovy.typecheckers.MonadicChecker')
Option<Integer> halveTwice(int n) {
    DO(a in halveIfEven.f(n),
       b in halveIfEven.f(a)) {
        Option.some(b)
    }
}
assert halveTwice(20) == Option.some(5)

// 3. Same shape with an FJ Validation gives applicative-flavoured errors
Validation<String, Integer> okOrLow(int n) {
    n > 0 ? Validation.success(n) : Validation.fail("non-positive: $n".toString())
}
@TypeChecked(extensions = 'groovy.typecheckers.MonadicChecker')
Validation<String, Integer> addPositives(int x, int y) {
    DO(a in okOrLow(x),
       b in okOrLow(y)) {
        Validation.success(a + b)
    }
}
assert addPositives(2, 3).successE() == 5
assert addPositives(-1, 3).failE()   == 'non-positive: -1'

// 4. The argument for new code:
// Where FunctionalJava forces you to inherit fj.* types (or wrap),
// Groovy 6's structural participation lets your OWN type act monadically.
// See `@groovy.transform.Monadic` in the GEP-23 spec for the opt-in.
println 'FunctionalJava contrast OK.'
