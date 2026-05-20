import groovy.transform.TypeChecked
import static org.apache.groovy.macrolib.MacroLibGroovyMethods.DO

// Scala:
//   for { a <- m1; b <- f(a); c <- g(a, b) } yield body(a, b, c)
// Haskell:
//   do a <- m1
//      b <- f a
//      c <- g a b
//      pure (body a b c)
// Groovy 6 (GEP-23):
//   DO(a in m1, b in f(a), c in g(a, b)) { body(a, b, c) }

@TypeChecked(extensions = 'groovy.typecheckers.MonadicChecker')
Optional<String> greet(Map<String, String> users, String userId) {
    DO(user  in Optional.ofNullable(users[userId]),
       name  in Optional.ofNullable(user.split(/\|/)[0])) {
        Optional.of("Hello, $name!".toString())
    }
}

def users = [u42: 'Alice|admin', u7: 'Bob|guest']
assert greet(users, 'u42').get() == 'Hello, Alice!'
assert greet(users, 'u99') == Optional.empty()    // short-circuits — body never runs

println 'Optional DO OK.'
