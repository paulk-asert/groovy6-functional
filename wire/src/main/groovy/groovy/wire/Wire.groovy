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

// User-facing entry point for the builder form.
//
//   def g = Wire.wire('wordCount') {
//       task Primitives::readPath
//       task Primitives::loadText
//       task Primitives::countWords
//       ...
//   }
//
//   g.run(...)        // execute
//   g.toPlantUml()    // render structure
//
// The closure delegates to a WireBuilder. Each `task ref` call adds a
// primitive; the builder verifies the data-flow contract on the fly
// using the @Wirable annotations the primitive itself carries.
//
// The WIRE macro in :wire-macro provides a proc-notation
// (`WIRE { name << call(args) }`) that compiles to real async/Dataflows
// code without needing @Wirable annotations on the primitives — see
// :wire-demo-macro. The builder form here stays useful for constructing
// graphs programmatically from data.
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
