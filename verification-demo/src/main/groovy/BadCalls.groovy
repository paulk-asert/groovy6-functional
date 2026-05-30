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
 * Compiles a series of "intentionally wrong" call sites and prints the
 * diagnostics VerifyChecker emits for each. These wrong sources are
 * compiled on the fly so the demo module itself stays green — the
 * checker turns each bad call into a compile error, which we catch and
 * print rather than let break the build.
 *
 * The headline case is the off-by-one: a developer guards the call
 * with `if (n != 0)`, thinking that protects the square root — but the
 * checker replies with a counterexample, n = -1. Tightening the guard
 * to `n >= 0` (see GoodCalls.viaIfCondition) makes it pass.
 *
 * Run with:
 *   ../gradlew :verification-demo:run.BadCalls
 */
class BadCalls {

    static final List<Map> CASES = [
        [
            name: "Off-by-one: condition is too weak (the 'almost correct' one)",
            // n != 0 doesn't imply n >= 0 (n could be -3).
            expect: "counterexample",
            source: '''
                import groovy.transform.TypeChecked
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class BadWeakCondition {
                    static int go(int n) {
                        if (n != 0) {
                            return ISqrt.isqrt(n)
                        }
                        0
                    }
                }
            '''.stripIndent()
        ],
        [
            name: "Literal that violates the precondition",
            expect: "counterexample",
            source: '''
                import groovy.transform.TypeChecked
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class BadLiteral {
                    static int go() { ISqrt.isqrt(-1) }
                }
            '''.stripIndent()
        ],
        [
            name: "Symbolic argument with no facts in scope",
            expect: "Cannot prove",
            source: '''
                import groovy.transform.TypeChecked
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class BadUnconstrained {
                    static int go(int n) { ISqrt.isqrt(n) }
                }
            '''.stripIndent()
        ],
        [
            name: "Enclosing condition that contradicts the precondition",
            expect: "counterexample",
            source: '''
                import groovy.transform.TypeChecked
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class BadContradiction {
                    static int go(int n) {
                        if (n < 0) {
                            return ISqrt.isqrt(n)
                        }
                        0
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
                gcl.parseClass((String) c.source, "BadCalls_case${i + 1}.groovy")
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
        println failed == 0 ? "All bad-call cases produced the expected diagnostics." :
                              "${failed} case(s) did not produce the expected diagnostic."
        if (failed > 0) System.exit(1)
    }
}
