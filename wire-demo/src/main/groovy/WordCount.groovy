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
import groovy.wire.Wire

// The consumer side. Same annotated primitives, two views:
//
//   wc.run([:])          executes the graph (prints a small report)
//   wc.toPlantUml()      emits the structural form as PlantUML
//
// Both come from one definition. That structural equivalence is the
// property WireCat (the Haskell plugin this example is modelled on)
// preserves by restricting its "cartesian categories" to compositions
// of named primitives — and the property a `WireChecker` type-checking
// extension would lift to compile-time guarantees here.

def wc = Wire.wire('WordCount') {
    step WordCountPrimitives::readPath
    step WordCountPrimitives::loadText
    step WordCountPrimitives::countWords
    step WordCountPrimitives::countLines
    step WordCountPrimitives::countChars
    step WordCountPrimitives::writeReport
}

// Emit the PlantUML alongside the source so the blog post can include
// the file via `include::` and so the source-of-truth diagram lives
// in git, regenerated on every build rather than hand-maintained.
def out = new File('diagrams/WordCount.puml')
out.parentFile.mkdirs()
out.text = wc.toPlantUml()
println "wrote ${out.canonicalPath}"
println()
println wc.toPlantUml()

// Run it.
wc.run([:])
println 'WordCount wire OK.'
