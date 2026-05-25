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

import groovy.transform.CompileStatic

// A captured cartesian-style composition of @Wirable primitives.
//
//   .run(env)       executes the steps in declared order, threading a
//                   Map<String,Object> environment through them; each
//                   primitive consumes the fields named in its @Wirable
//                   inputs and contributes its outputs back.
//   .toPlantUml()   emits the structural form of the graph as PlantUML.
//                   The two views are derived from one definition — the
//                   same property WireCat preserves by forbidding `arr`.
@CompileStatic
class WireGraph {
    String         name
    List<WireNode> nodes

    Map<String, Object> run(Map<String, Object> input = [:]) {
        Map<String, Object> env = [:]
        env.putAll(input)
        for (WireNode node : nodes) {
            Map<String, Object> args = [:]
            for (String field : node.inputs) {
                if (!env.containsKey(field)) {
                    throw new IllegalStateException(
                        "wire '$name': step '$node.name' needs field '$field' " +
                        "but env only has ${env.keySet()}")
                }
                args[field] = env[field]
            }
            def result = node.invoker.call(args)
            if (result == null) {
                continue
            }
            if (result instanceof Map) {
                env.putAll((Map<String, Object>) result)
                continue
            }
            // record-like: respond to .toMap()
            try {
                def m = result.invokeMethod('toMap', null as Object[])
                if (m instanceof Map) {
                    env.putAll((Map<String, Object>) m)
                    continue
                }
            } catch (ignored) {
                // fall through
            }
            // single-output convenience
            if (node.outputs.size() == 1) {
                env.put(node.outputs[0], result)
            }
        }
        env
    }

    String toPlantUml() {
        PlantUmlRenderer.render(this)
    }
}
