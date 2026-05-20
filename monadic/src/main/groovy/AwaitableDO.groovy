import groovy.concurrent.Awaitable
import groovy.transform.TypeChecked
import static groovy.concurrent.AsyncSupport.async
import static org.apache.groovy.macrolib.MacroLibGroovyMethods.DO

// Composing Awaitables with DO. The dependency between the carrier
// values is in the source — no .thenCompose chains, no callbacks.

Awaitable<Integer> fetchId(String key)        { async { key.hashCode() & 0xff } }
Awaitable<String>  fetchName(int id)          { async { "user-$id".toString() } }
Awaitable<String>  greeting(String name)      { async { "Hello, $name!" } }

@TypeChecked(extensions = 'groovy.typecheckers.MonadicChecker')
Awaitable<String> greetByKey(String key) {
    DO(id   in fetchId(key),
       name in fetchName(id),
       msg  in greeting(name)) {
        async { msg }
    }
}

assert greetByKey('alice').await() == 'Hello, user-29!'
println 'Awaitable DO OK.'

// Same shape as the Optional example. That's the point of DO.
