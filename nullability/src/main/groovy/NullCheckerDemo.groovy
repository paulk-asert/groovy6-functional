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
