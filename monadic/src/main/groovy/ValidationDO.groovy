import fj.data.Validation
import groovy.transform.TypeChecked
import static org.apache.groovy.macrolib.MacroLibGroovyMethods.DO

// FunctionalJava's Validation is in the standard allow-list (by name).
// DO sees fj.data.Validation, picks fj.F-shaped bind/map, and the rest
// is the same syntax we used for Optional and Awaitable.

Validation<String, Integer> parsePositive(String s) {
    try {
        var n = s as int
        n > 0 ? Validation.success(n) : Validation.fail("not positive: $s".toString())
    } catch (NumberFormatException ignored) {
        Validation.fail("not numeric: $s".toString())
    }
}

@TypeChecked(extensions = 'groovy.typecheckers.MonadicChecker')
Validation<String, Integer> add(String a, String b) {
    DO(x in parsePositive(a),
       y in parsePositive(b)) {
        Validation.success(x + y)
    }
}

assert add('2', '3').successE() == 5
assert add('hi', '3').failE()   == 'not numeric: hi'

println 'Validation DO OK.'
