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

import groovy.concurrent.Dataflows

// Result of a WIRE macro expansion.
//
//   graph     — structural form captured at compile time (used by
//               toPlantUml; nodes have null invokers since execution
//               doesn't go through them)
//   executor  — closure emitted by the macro that performs the real
//               async/Dataflows scheduling, given a fresh Dataflows
//               with the graph-level inputs already seeded
//
// .run(inputs) constructs the Dataflows, pre-binds the supplied
// inputs, invokes the executor, and returns the resulting bindings
// as a plain Map — same shape WireGraph.run() returns for the
// builder form.
class WireResult {
    WireGraph graph
    Closure<Map<String, Object>> executor

    String toPlantUml() {
        graph.toPlantUml()
    }

    Map<String, Object> run(Map<String, Object> inputs = [:]) {
        def df = new Dataflows()
        inputs.each { String k, Object v -> df[k] = v }
        executor.call(df)
    }
}
