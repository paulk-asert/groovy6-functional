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
package domain

import groovy.wire.Wirable
import groovy.wire.WireSource

// Producer-side primitives. Each method returns its single output
// value directly (not a Map<String,?>) — the binding name is supplied
// either by @Wirable.outputs (builder form) or by the LHS of the
// macro-form assignment (`text = loadText(path)`).
//
// @Wirable.inputs/outputs are still useful for the builder DSL form,
// which composes primitives via method references and infers the
// wiring from these declarations. The macro form ignores them: the
// wiring there is the source code itself.
@WireSource(description = 'Word-count pipeline primitives')
class WordCountPrimitives {

    @Wirable(inputs = [], outputs = ['path'],
             description = 'Reads the path from the WC_FILE env var ' +
                           'or falls back to the bundled sample.txt resource.')
    static String readPath() {
        String fromEnv = System.getenv('WC_FILE')
        String fallback = WordCountPrimitives.classLoader
                              .getResource('sample.txt')?.toURI()?.path
        fromEnv ?: fallback
    }

    @Wirable(inputs = ['path'], outputs = ['text'])
    static String loadText(String path) {
        new File(path).text
    }

    @Wirable(inputs = ['text'], outputs = ['words'])
    static int countWords(String text) {
        text.split(/\s+/).findAll { it }.size()
    }

    @Wirable(inputs = ['text'], outputs = ['lines'])
    static int countLines(String text) {
        text.readLines().size()
    }

    @Wirable(inputs = ['text'], outputs = ['chars'])
    static int countChars(String text) {
        text.length()
    }

    @Wirable(inputs = ['path', 'words', 'lines', 'chars'], outputs = [])
    static void writeReport(String path, int words, int lines, int chars) {
        println "${path}: ${words} words, ${lines} lines, ${chars} chars"
    }
}
