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

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

// Marks a method as a wirable primitive.
//
// inputs[]  — names of fields the method reads from the wire env;
//             must match parameter names (with `groovyOptions.parameters = true`)
//             unless explicitly overridden.
// outputs[] — names of fields the method contributes back to the env;
//             match the keys of the returned Map (or the components of a
//             returned record, via toMap()).
//
// Both are intentionally explicit: the WireBuilder treats them as the
// declared contract and refuses to schedule a graph whose flow does not
// match. Same producer-side / consumer-side split as @Pure / @Reducer.
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD])
@interface Wirable {
    String   name()        default ''
    String[] inputs()      default []
    String[] outputs()     default []
    String   description() default ''
}
