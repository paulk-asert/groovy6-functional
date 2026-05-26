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

import java.lang.reflect.Method
import org.codehaus.groovy.runtime.MethodClosure

// Captures a sequence of @Wirable primitives into a WireGraph.
//
// Each `task` call:
//   * reads @Wirable.inputs/outputs from the primitive's annotation;
//   * sanity-checks that every required input is either a declared
//     graph-level input or contributed by some earlier task — caught
//     at build time, the runtime-deferred form of what WireChecker
//     does at compile time;
//   * captures an invoker that, at run() time, marshals env -> args
//     and calls the primitive synchronously inside an async task.
//
// `task` is the wire builder's graph-node verb. Each call is
// scheduled internally via Groovy 6's `async { … }` and
// `groovy.concurrent.DataflowVariable`, so independent tasks run
// concurrently on the JDK's virtual-thread executor. Execution
// order is data-driven (dependencies on @Wirable.inputs/outputs),
// not textual; declaration order is a hint, not a contract.
class WireBuilder {

    private final String graphName
    private final List<WireNode> nodes = []
    private final Set<String> available = new LinkedHashSet<>()
    private final List<String> declaredInputs

    WireBuilder(String name, List<String> declaredInputs = []) {
        this.graphName = name
        this.declaredInputs = declaredInputs
        this.available.addAll(declaredInputs)
    }

    // Add a primitive to the graph. The wiring is implicit from
    // @Wirable inputs/outputs on the method.
    void task(MethodClosure ref) {
        Method m = findMethod(ref)
        Wirable w = m.getAnnotation(Wirable)
        if (w == null) {
            throw new IllegalStateException(
                "task ${ref.method}: not annotated @Wirable")
        }
        String label = w.name() ?: m.name
        List<String> inputs  = w.inputs()  as List<String>
        List<String> outputs = w.outputs() as List<String>
        addTaskNode(label, inputs, outputs, ref.owner, m)
    }

    // Explicit form used by the WIRE macro, which derives the wiring
    // from source-level bindings (LHS of `name = call(args)`) rather
    // than from the primitive's @Wirable declarations. Both forms
    // produce identical WireNodes.
    void task(String outputName, MethodClosure ref, List<String> inputs) {
        Method m = findMethod(ref)
        String label = m.name
        List<String> outputs = outputName ? [outputName] : []
        addTaskNode(label, inputs, outputs, ref.owner, m)
    }

    private void addTaskNode(String label, List<String> inputs, List<String> outputs,
                             Object owner, Method m) {
        for (String field : inputs) {
            if (!available.contains(field)) {
                throw new IllegalStateException(
                    "wire '$graphName': task '$label' needs field '$field' " +
                    "but only ${available} are available so far " +
                    "(declared graph inputs: ${declaredInputs})")
            }
        }
        Closure<?> invoker = { Map<String, Object> args ->
            invokePrimitive(m, owner, args)
        }
        nodes << new WireNode(
            name:    label,
            inputs:  inputs,
            outputs: outputs,
            invoker: invoker
        )
        available.addAll(outputs)
    }

    WireGraph build() {
        new WireGraph(name: graphName, nodes: nodes, declaredInputs: declaredInputs)
    }

    // --- internals ---

    private static Object invokePrimitive(Method m, Object owner, Map<String, Object> args) {
        def params = m.parameters
        if (params.length == 0) {
            return m.invoke(owner)
        }
        if (params.length == 1 && Map.isAssignableFrom(params[0].type)) {
            return m.invoke(owner, args)
        }
        // Match by parameter name (requires groovyOptions.parameters = true)
        Object[] positional = new Object[params.length]
        for (int i = 0; i < params.length; i++) {
            String pname = params[i].name
            if (!args.containsKey(pname)) {
                throw new IllegalStateException(
                    "primitive ${m.declaringClass.simpleName}.${m.name}: " +
                    "no env value for parameter '${pname}'")
            }
            positional[i] = args[pname]
        }
        m.invoke(owner, positional)
    }

    private static Method findMethod(MethodClosure ref) {
        String target = ref.method
        Class<?> clazz = (ref.owner instanceof Class) ? (Class) ref.owner : ref.owner.getClass()
        def candidates = clazz.methods.findAll { it.name == target }
        if (candidates.size() == 1) return candidates[0]
        def annotated = candidates.findAll { it.isAnnotationPresent(Wirable) }
        if (annotated.size() == 1) return annotated[0]
        throw new IllegalStateException(
            "cannot resolve a unique @Wirable method for ${clazz.simpleName}::${target}")
    }
}
