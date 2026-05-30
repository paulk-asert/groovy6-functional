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
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage

/**
 * Compiles intentionally-wrong method bodies and prints the
 * postcondition diagnostics VerifyChecker emits. As with BadCalls,
 * these are compiled on the fly so the demo module stays green — a
 * refuted postcondition is a compile error, caught and printed here.
 *
 * The headline case is `maxBuggy`: it returns the wrong branch, so its
 * `@Ensures({ result >= a && result >= b })` fails — and the checker
 * hands back the concrete `a`/`b` that break it.
 *
 * Run with:
 *   ../gradlew :verification-demo:run.BadEnsures
 */
class BadEnsures {

    static final List<Map> CASES = [
        [
            name: "maxBuggy: returns the smaller branch",
            expect: "counterexample",
            source: '''
                import groovy.transform.TypeChecked
                import verification.Ensures
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class MaxBuggy {
                    @Ensures({ result >= a && result >= b })
                    static int max(int a, int b) {
                        if (a > b) b else a   // wrong branches
                    }
                }
            '''.stripIndent()
        ],
        [
            name: "absBuggy: comparison reversed (fails on both paths)",
            expect: "counterexample",
            source: '''
                import groovy.transform.TypeChecked
                import verification.Ensures
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class AbsBuggy {
                    @Ensures({ result >= 0 })
                    static int abs(int x) {
                        if (x > 0) -x else x   // should be x < 0: as written, wrong for every nonzero x
                    }
                }
            '''.stripIndent()
        ],
        [
            name: "incBuggy: wrong constant in straight-line body",
            expect: "counterexample",
            source: '''
                import groovy.transform.TypeChecked
                import verification.Ensures
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class IncBuggy {
                    @Ensures({ result == x + 1 })
                    static int inc(int x) {
                        int y = x + 2   // should be x + 1
                        y
                    }
                }
            '''.stripIndent()
        ],
    ]

    static void main(String[] args) {
        int failed = 0
        CASES.eachWithIndex { Map c, int i ->
            println "=" * 70
            println "Case ${i + 1}: ${c.name}"
            println "-" * 70

            GroovyClassLoader gcl = new GroovyClassLoader(
                Thread.currentThread().contextClassLoader)
            try {
                gcl.parseClass((String) c.source, "BadEnsures_case${i + 1}.groovy")
                println "  [!] UNEXPECTED: compilation succeeded — checker missed this one."
                failed++
            } catch (MultipleCompilationErrorsException e) {
                List<String> messages = e.errorCollector.errors.collect { err ->
                    if (err instanceof SyntaxErrorMessage) {
                        err.cause.message
                    } else {
                        err.toString()
                    }
                }
                messages.each { m -> println "  ${m.replaceAll(/\n/, '\n  ')}" }
                String all = messages.join("\n")
                if (all.contains((String) c.expect)) {
                    println "  [ok] Found expected substring: '${c.expect}'"
                } else {
                    println "  [!] MISSING expected substring: '${c.expect}'"
                    failed++
                }
            } catch (CompilationFailedException e) {
                println "  Compilation failed: ${e.message}"
            } finally {
                try { gcl.close() } catch (Throwable ignored) {}
            }
            println ""
        }

        println "=" * 70
        println failed == 0 ? "All bad-postcondition cases produced the expected diagnostics." :
                              "${failed} case(s) did not produce the expected diagnostic."
        if (failed > 0) System.exit(1)
    }
}
