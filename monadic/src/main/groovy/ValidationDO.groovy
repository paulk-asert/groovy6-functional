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

assert add('2', '3').success() == 5
assert add('hi', '3').fail()   == 'not numeric: hi'

println 'Validation DO OK.'
