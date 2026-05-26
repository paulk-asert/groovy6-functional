<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

groovy6-functional
==================

Companion code for the blog post
**"Groovy 6 features for Functional Programmers"**.

Each subproject is a small, single-topic example set:

| Module          | Theme                                                     |
|-----------------|-----------------------------------------------------------|
| `monoids`       | `@Associative`, `@Reducer`, `CombinerChecker`             |
| `purity`        | `@Pure`, `@Modifies`, `PurityChecker`, `ModifiesChecker`  |
| `monadic`       | `DO` macro (GEP-23), `MonadicChecker`, `MonadicShapeChecker` |
| `nullability`   | `NullChecker` — strict mode and annotation mode           |
| `immutability`  | `val`, nested `copyWith`, destructuring, `@Immutable`     |
| `recursion`     | `@TailRecursive`, `@Decreases`, `@Invariant` on loops     |
| `contrast`      | Side-by-side with FunctionalJava and highj                |
| `pbt`           | Property-based tests derived from `@Associative`/`@Reducer` annotations |
| `wire`          | User-built checked DSL: `@Wirable` annotations, builder, dataflow runtime, PlantUML renderer |
| `wire-checker`  | `WireChecker` type-checking extension for the builder form |
| `wire-macro`    | `WIRE { name << call(args) }` proc-notation macro that compiles to `async`/`Dataflows` code |
| `wire-demo`     | Consumer demo using the `Wire.wire { task X::y }` builder |
| `wire-demo-checked` | Same builder under `@TypeChecked(extensions = '…WireChecker.groovy')` |
| `wire-demo-macro` | Consumer demo using the `WIRE` macro |

The default Groovy version is `6.0.0-alpha-3`; override with
`-PgroovyVersion=...` on the Gradle CLI.

Most modules contain runnable scripts; from a module directory:

    ../gradlew run            # if a main is configured
    ../gradlew compileGroovy  # always works — compile-time checkers
                              # are the point of most examples
