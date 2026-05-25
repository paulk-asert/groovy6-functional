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
package groovy.wire

// User-facing entry point.
//
//   def g = Wire.wire('wordCount') {
//       step Primitives::readPath
//       step Primitives::loadText
//       step Primitives::countWords
//       ...
//   }
//
//   g.run(...)        // execute
//   g.toPlantUml()    // render structure
//
// The closure delegates to a WireBuilder. Each `step ref` call adds a
// primitive; the builder verifies the data-flow contract on the fly
// using the @Wirable annotations the primitive itself carries.
//
// A future macro form (`WIRE { readPath(); loadText(path); ... }`)
// would desugar to the same builder calls but lift the primitive
// invocations into the source language directly. The macro needs to
// live in its own subproject so the AST transform is compiled before
// the code that uses it; the builder API shown here works without
// any compile-time machinery.
class Wire {

    static WireGraph wire(String name,
                          List<String> inputs = [],
                          @DelegatesTo(WireBuilder) Closure body) {
        def b = new WireBuilder(name, inputs)
        body.delegate = b
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.call()
        b.build()
    }

    static WireGraph wire(Map opts,
                          String name,
                          @DelegatesTo(WireBuilder) Closure body) {
        wire(name, (opts.inputs ?: []) as List<String>, body)
    }
}
