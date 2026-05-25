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

// Render a WireGraph as a PlantUML component diagram.
//
// Each primitive is a `[Node]` component; each data dependency is a
// labelled edge `[from] --> [to] : fieldName`. Fields needed by the
// graph but produced by no earlier node become external inputs drawn
// as small open circles (`()`). Fields produced but never consumed
// downstream become external outputs.
//
// The result is plain PlantUML, suitable for paste into:
//   * an asciidoc [plantuml] block,
//   * a markdown ```plantuml fenced block,
//   * a standalone .puml file for the PlantUML CLI/server.
@CompileStatic
class PlantUmlRenderer {

    static String render(WireGraph g) {
        def sb = new StringBuilder()
        String title = g.name ?: 'wire'
        sb << "@startuml ${title}\n"
        sb << "left to right direction\n"
        sb << "skinparam shadowing false\n"
        sb << "skinparam componentStyle rectangle\n"
        sb << "skinparam component {\n"
        sb << "  BackgroundColor #FAFAFA\n"
        sb << "  BorderColor #777777\n"
        sb << "}\n\n"

        // Nodes
        for (WireNode n : g.nodes) {
            sb << "component \"${n.name}\" as ${safe(n.name)}\n"
        }
        sb << '\n'

        // Walk in order, tracking which step produced each field most recently.
        // Fields read before any producer become external inputs.
        Map<String, String> origin = [:]
        Set<String> producedAtAll = new LinkedHashSet<>()
        Set<String> consumed = new LinkedHashSet<>()

        for (WireNode n : g.nodes) {
            for (String field : n.inputs) {
                consumed << field
                String src = origin.get(field)
                if (src == null) {
                    String inId = "in_${safe(field)}"
                    sb << "() \"${field}\" as ${inId}\n"
                    sb << "${inId} --> ${safe(n.name)}\n"
                } else {
                    sb << "${safe(src)} --> ${safe(n.name)} : ${field}\n"
                }
            }
            for (String field : n.outputs) {
                origin.put(field, n.name)
                producedAtAll << field
            }
        }

        // External outputs: produced but never consumed downstream.
        def terminal = producedAtAll - consumed
        if (!terminal.isEmpty()) {
            sb << '\n'
            for (String field : terminal) {
                String outId = "out_${safe(field)}"
                sb << "() \"${field}\" as ${outId}\n"
                sb << "${safe(origin[field])} --> ${outId} : ${field}\n"
            }
        }

        sb << "@enduml\n"
        sb.toString()
    }

    private static String safe(String s) {
        s.replaceAll(/[^A-Za-z0-9_]/, '_')
    }
}
