import groovy.transform.TypeChecked
import org.jspecify.annotations.NonNull
import org.jspecify.annotations.Nullable

// Maybe / Option without the wrapper.
// Haskell:                  safeLen :: Maybe String -> Int
// Scala:                    def safeLen(s: Option[String]): Int
// Groovy 6 + NullChecker:   the compiler tracks nullability through
//                            assignments and guards — no monad needed
//                            for the common "did this exist?" case.

@TypeChecked(extensions = 'groovy.typecheckers.NullChecker')
int safeLength(@Nullable String text) {
    if (text != null) {
        return text.length()         // ok — narrowed to @NonNull
    }
    -1
}

@TypeChecked(extensions = 'groovy.typecheckers.NullChecker(strict: true)')
def strictDemo() {
    def x = null
    // x.toString()                  // compile error: x may be null
    x = 'hello'
    assert x.toString() == 'hello'   // ok — reassigned non-null
}

assert safeLength('hello') == 5
assert safeLength(null)    == -1
strictDemo()
println 'NullChecker demo OK.'
