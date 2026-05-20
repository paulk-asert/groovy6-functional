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

The default Groovy version is `6.0.0-alpha-3`; override with
`-PgroovyVersion=...` on the Gradle CLI.

Most modules contain runnable scripts; from a module directory:

    ../gradlew run            # if a main is configured
    ../gradlew compileGroovy  # always works — compile-time checkers
                              # are the point of most examples
