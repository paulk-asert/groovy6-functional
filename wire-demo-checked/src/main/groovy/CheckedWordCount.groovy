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
import domain.WordCountPrimitives
import groovy.transform.TypeChecked
import groovy.wire.Wire

// Same @Wirable primitives as :wire-demo, same `Wire.wire { ... }`
// shape — but now the field-flow check fires at *compile* time, via
// the WireChecker type-checking extension shipped as a classpath
// resource by :wire-checker. The runtime builder check still exists
// as a fail-safe; the compile-time check just brings the diagnostic
// forward.
//
// The wiring here declares `path` as a graph-level input rather than
// running readPath — partly to demonstrate that the checker honours
// declared inputs, partly to keep the demo runnable when sample.txt
// is reached via a classloader chain that gives back a `jar:` URI.

@TypeChecked(extensions = 'groovy/wire/WireChecker.groovy')
def buildOk() {
    Wire.wire('CheckedWordCount', ['path']) {
        task WordCountPrimitives::loadText
        task WordCountPrimitives::countWords
        task WordCountPrimitives::countLines
        task WordCountPrimitives::countChars
        task WordCountPrimitives::writeReport
    }
}

def sample = File.createTempFile('checked-wc', '.txt')
sample.deleteOnExit()
sample.text = '''\
The quick brown fox jumps over the lazy dog.
Pack my box with five dozen liquor jugs.
The five boxing wizards jump quickly.
'''

def wc = buildOk()
wc.run(path: sample.canonicalPath)
println 'Checked WordCount OK — wired and run under WireChecker.'

// ---- Rejection demo (uncomment to see the checker fire) -----------
//
// @TypeChecked(extensions = 'groovy/wire/WireChecker.groovy')
// def buildBroken() {
//     Wire.wire('Broken', ['path']) {
//         task WordCountPrimitives::countWords      // needs 'text'
//         task WordCountPrimitives::loadText        // produces 'text'
//     }
// }
//
// Compiling the block above fails with:
//
//   wire task 'countWords' needs field 'text' but only [path] have
//   been produced so far
//
// Same message the runtime WireBuilder would have raised on the
// first call — now caught before the program ever runs.
