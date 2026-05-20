/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
