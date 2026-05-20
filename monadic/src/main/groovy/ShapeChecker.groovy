import groovy.transform.TypeChecked

// MonadicShapeChecker lints hand-written native chains across the same
// carrier set — for codebases that prefer .flatMap/.map over DO.
//
// Three classes of error it catches:
//
//   1. bind returning a non-carrier
//        Optional.of(1).flatMap { it + 1 }            // <- compile error
//
//   2. bind returning a different carrier
//        Stream.of(1).flatMap { Optional.of(it) }     // <- compile error
//
//   3. map returning the same carrier (M<M<T>> foot-gun)
//        Optional.of(1).map { Optional.of(it) }       // <- compile error
//
// The same registry powers MonadicChecker and MonadicShapeChecker,
// so user types declared with @Monadic participate in both.

@TypeChecked(extensions = 'groovy.typecheckers.MonadicShapeChecker')
def cleanChain() {
    var u = Optional.of('alice|admin')
                    .flatMap { Optional.of(it.split(/\|/)[0]) }    // ok
                    .map     { name -> "Hello, $name!" }           // ok
    assert u.get() == 'Hello, alice!'
}

cleanChain()
println 'Shape checker OK.'
