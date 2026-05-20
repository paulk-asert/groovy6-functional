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
import groovy.concurrent.Awaitable
import groovy.transform.TypeChecked
import static org.apache.groovy.runtime.async.AsyncSupport.async
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

assert greetByKey('alice').get() == 'Hello, user-128!'
println 'Awaitable DO OK.'

// Same shape as the Optional example. That's the point of DO.
