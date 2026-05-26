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
import static groovy.wire.macro.WireMacroMethods.WIRE

// << binding form. At compile time:
//
//   text << loadText(path)
//
// expands to roughly:
//
//   __tasks << async { __df.text = WordCountPrimitives.loadText(__df.path) }
//
// — real Groovy 6 dataflow code, with the macro auto-adding the
// `async { }` wrapping, the `__df.` prefixes, and the qualifying
// `WordCountPrimitives.` receiver. Read top-to-bottom as a sequence
// of bindings; executed concurrently where dependencies allow,
// gated by groovy.concurrent.DataflowVariable.

def sample = File.createTempFile('macro-wc', '.txt')
sample.deleteOnExit()
sample.text = '''\
The quick brown fox jumps over the lazy dog.
Pack my box with five dozen liquor jugs.
The five boxing wizards jump quickly.
'''

def wc = WIRE(WordCountPrimitives, 'WordCount', ['path']) {
    text  << loadText(path)
    words << countWords(text)
    lines << countLines(text)
    chars << countChars(text)
    writeReport(path, words, lines, chars)
}

println wc.toPlantUml()
def env = wc.run(path: sample.canonicalPath)
println "(env: ${env.subMap(['words','lines','chars'])})"
println 'WordCount via WIRE macro OK.'
