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

import groovy.concurrent.DataflowVariable

import static org.apache.groovy.runtime.async.AsyncSupport.async
import static org.apache.groovy.runtime.async.AsyncSupport.await

// A captured composition of @Wirable primitives.
//
//   .run(env)       schedules each task on the async executor, with
//                   inter-task dependencies expressed as
//                   DataflowVariables keyed on field name.
//                   Independent tasks run concurrently — the data
//                   dependency declared by @Wirable.inputs/outputs is
//                   what gates execution, not textual order.
//   .toPlantUml()   emits the structural form of the graph as
//                   PlantUML. Same value, different interpretation —
//                   the property WireCat preserves by forbidding
//                   `arr`.
class WireGraph {
    String         name
    List<WireNode> nodes
    List<String>   declaredInputs = []

    Map<String, Object> run(Map<String, Object> input = [:]) {
        // One DataflowVariable per field — graph-level inputs preseeded
        // from the supplied env, task outputs filled in by the
        // scheduled tasks as they complete.
        Map<String, DataflowVariable> bindings = [:]
        for (String field : declaredInputs) {
            def v = new DataflowVariable()
            if (input.containsKey(field)) v << input[field]
            bindings[field] = v
        }
        for (WireNode node : nodes) {
            for (String field : node.outputs) {
                bindings.computeIfAbsent(field) { new DataflowVariable() }
            }
        }

        // Schedule one async task per node. Each blocks on its declared
        // inputs (awaiting their DataflowVariables), runs the primitive,
        // and binds its declared outputs.
        // Note: nodes.each gives a fresh closure-captured variable per
        // iteration; a plain for-loop here would alias the loop variable
        // into all async closures.
        def tasks = []
        nodes.each { WireNode n ->
            tasks << async { ->
                Map<String, Object> args = [:]
                n.inputs.each { String field ->
                    args[field] = await(bindings[field])
                }
                def result = n.invoker.call(args)
                Map<String, Object> produced = unpack(result, n.outputs)
                n.outputs.each { String field ->
                    bindings[field] << (produced.containsKey(field) ? produced[field] : null)
                }
                null
            }
        }

        // Await every scheduled task so run() is synchronous to its
        // caller. Surface any failure raised by a task.
        tasks.each { await(it) }

        // Materialise the env from all fulfilled bindings.
        Map<String, Object> env = [:]
        bindings.each { String k, DataflowVariable v -> env[k] = v.get() }
        env
    }

    String toPlantUml() {
        PlantUmlRenderer.render(this)
    }

    private static Map<String, Object> unpack(Object result, List<String> outputs) {
        if (result == null) return [:]
        if (result instanceof Map) return (Map<String, Object>) result
        try {
            def m = result.invokeMethod('toMap', null as Object[])
            if (m instanceof Map) return (Map<String, Object>) m
        } catch (ignored) { /* fall through */ }
        if (outputs.size() == 1) return [(outputs[0]): result]
        [:]
    }
}
