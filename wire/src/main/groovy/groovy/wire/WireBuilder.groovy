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

// Builds a WireGraph from a sequence of method references to @Wirable
// methods.
//
// Each step:
//   * reads @Wirable.inputs/outputs (or infers them from the method
//     signature when not given explicitly);
//   * verifies that every required input has been produced by some
//     earlier step OR is declared as a graph-level input;
//   * captures a closure that, at run time, marshals env -> args,
//     invokes the primitive, and returns the result for unpacking.
//
// The build-time check is the fail-fast equivalent of what a
// `WireChecker` type-checking extension would do at compile time:
// same producer/consumer split, same contract, just at a different
// phase. Both can coexist (compile-time for static feedback in IDE,
// build-time for the dynamic-DSL form shown here).
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

    // Add a primitive to the graph. Returns void; the wiring is
    // implicit from @Wirable inputs/outputs on the method.
    void step(MethodClosure ref) {
        Method m = findMethod(ref)
        Wirable w = m.getAnnotation(Wirable)
        if (w == null) {
            throw new IllegalStateException(
                "step ${ref.method}: not annotated @Wirable")
        }
        String label = w.name() ?: m.name
        List<String> inputs  = w.inputs()  as List<String>
        List<String> outputs = w.outputs() as List<String>

        for (String field : inputs) {
            if (!available.contains(field)) {
                throw new IllegalStateException(
                    "wire '$graphName': step '$label' needs field '$field' " +
                    "but only ${available} are available so far " +
                    "(declared graph inputs: ${declaredInputs})")
            }
        }

        Object owner = ref.owner
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
        new WireGraph(name: graphName, nodes: nodes)
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
        // pick the one carrying @Wirable, ties broken by parameter count
        def annotated = candidates.findAll { it.isAnnotationPresent(Wirable) }
        if (annotated.size() == 1) return annotated[0]
        throw new IllegalStateException(
            "cannot resolve a unique @Wirable method for ${clazz.simpleName}::${target}")
    }
}
