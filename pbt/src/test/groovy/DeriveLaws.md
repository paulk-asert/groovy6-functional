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

# Deriving property-based tests from Groovy 6 annotations

`@Associative` and `@Reducer` (and `@Pure`, `@Modifies`,
`@Requires`/`@Ensures`) are *machine-actionable*. An AI agent, or a small
ASM/Groovy walker, can synthesise a property-based test directly from
the annotation — no inspection of the method body needed.

## The recipe

Given a discovered annotation, emit the matching law:

| Annotation        | Law to assert                                              |
|-------------------|------------------------------------------------------------|
| `@Associative` on `f(a, b)`         | `f(f(a, b), c) == f(a, f(b, c))`         |
| `@Reducer(zero = z)` on `f(a, b)`   | `f(a, z) == a && f(z, a) == a`           |
| `@Pure` (no `allows`)               | `f(args)` twice ⇒ same value, no observable effect |
| `@Modifies({ [fields...] })`        | every field NOT in the set is bit-for-bit identical |

## Example prompt to an agent

```
You are given a Groovy 6 class. For each static method whose annotations
include `@groovy.transform.Associative`, emit a jqwik @Property method
asserting associativity over inputs of the method's parameter type. If
the same method also carries `@groovy.transform.Reducer(zero = "<expr>")`,
emit a second @Property asserting `<expr>` is the identity. The test class
must compile under JUnit 5/Jupiter with the jqwik engine.
```

That prompt produces `MonoidLawsTest.groovy` in this directory.

The agent never has to read the *body* of `Sum.add`. The compile-time
`CombinerChecker` is what makes the annotation trustworthy enough to
treat it as a specification.
