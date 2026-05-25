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

// The producer-side library: a handful of @Wirable primitives that
// will compose into the word-count pipeline. Each method declares
// the env fields it reads (inputs) and the env fields it
// contributes (outputs). The WireBuilder uses those declarations
// to wire the graph; PlantUmlRenderer uses them to label the
// diagram edges.
//
// In a production library these would also carry @Pure / @Modifies
// (most of them are pure; loadText and writeReport name their
// effects); the wire runtime can be taught to refuse to reorder
// non-@Pure steps without changing any primitive.
@WireSource(description = 'Word-count pipeline primitives')
class WordCountPrimitives {

    @Wirable(inputs = [], outputs = ['path'],
             description = 'Reads the path to analyse from the WC_FILE env var ' +
                           'or falls back to the bundled sample.txt resource.')
    static Map<String, ?> readPath() {
        String fromEnv = System.getenv('WC_FILE')
        String fallback = WordCountPrimitives.classLoader
                              .getResource('sample.txt')?.toURI()?.path
        [path: fromEnv ?: fallback]
    }

    @Wirable(inputs = ['path'], outputs = ['text'])
    static Map<String, ?> loadText(String path) {
        [text: new File(path).text]
    }

    @Wirable(inputs = ['text'], outputs = ['words'])
    static Map<String, ?> countWords(String text) {
        [words: text.split(/\s+/).findAll { it }.size()]
    }

    @Wirable(inputs = ['text'], outputs = ['lines'])
    static Map<String, ?> countLines(String text) {
        [lines: text.readLines().size()]
    }

    @Wirable(inputs = ['text'], outputs = ['chars'])
    static Map<String, ?> countChars(String text) {
        [chars: text.length()]
    }

    @Wirable(inputs = ['path', 'words', 'lines', 'chars'], outputs = [])
    static Map<String, ?> writeReport(String path, int words, int lines, int chars) {
        println "${path}: ${words} words, ${lines} lines, ${chars} chars"
        [:]
    }
}
