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
import fj.data.Option as FJOption
import org.highj.data.Maybe
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
// so user types declared with @Monadic participate in both — and
// third-party carriers like fj.data.Option and org.highj.data.Maybe
// are covered by name:
//
//        FJOption.some(1).map { FJOption.some(it) }   // <- compile error
//        Maybe.Just(1).map { Maybe.Just(it) }         // <- compile error

@TypeChecked(extensions = 'groovy.typecheckers.MonadicShapeChecker')
def cleanChain() {
    var u = Optional.of('alice|admin')
                    .flatMap { Optional.of(it.split(/\|/)[0]) }    // ok
                    .map     { name -> "Hello, $name!" }           // ok
    assert u.get() == 'Hello, alice!'
}

@TypeChecked(extensions = 'groovy.typecheckers.MonadicShapeChecker')
def cleanChainFJ() {
    var u = FJOption.some('alice|admin')
                    .bind { FJOption.some(it.split(/\|/)[0]) }     // ok
                    .map  { name -> "Hello, $name!".toString() }   // ok
    assert u.some() == 'Hello, alice!'
}

@TypeChecked(extensions = 'groovy.typecheckers.MonadicShapeChecker')
def cleanChainHighJ() {
    var u = Maybe.Just('alice|admin')
                 .bind { Maybe.Just(it.split(/\|/)[0]) }            // ok
                 .map  { name -> "Hello, $name!".toString() }       // ok
    assert u.get() == 'Hello, alice!'
}

cleanChain()
cleanChainFJ()
cleanChainHighJ()
println 'Shape checker OK.'
