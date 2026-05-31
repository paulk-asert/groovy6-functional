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
 * Compiles intentionally-broken loops and prints the diagnostic the verifier
 * emits for each — one per inductive obligation. Each mutant breaks exactly one
 * of establishment / preservation / progress / use, and the checker names which.
 * Compiled on the fly so the demo module itself stays green.
 *
 * Run with:
 *   ../gradlew :verification-demo:run.BadLoops
 */
class BadLoops {

    static final List<Map> CASES = [
        [
            name: "Establishment: invariant doesn't hold on entry (i starts at 0, not >= 1)",
            expect: "on entry",
            source: '''
                import groovy.transform.TypeChecked
                import groovy.contracts.Requires
                import groovy.contracts.Ensures
                import groovy.contracts.Invariant
                import groovy.contracts.Decreases
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class EstablishBad {
                    @Requires({ n >= 0 })
                    @Ensures({ result == n })
                    static int go(int n) {
                        int i = 0
                        @Invariant({ 1 <= i && i <= n })
                        @Decreases({ n - i })
                        while (i < n) { i = i + 1 }
                        return i
                    }
                }
            '''.stripIndent()
        ],
        [
            name: "Preservation: body steps by 2, so i <= n is not maintained",
            expect: "preserved",
            source: '''
                import groovy.transform.TypeChecked
                import groovy.contracts.Requires
                import groovy.contracts.Ensures
                import groovy.contracts.Invariant
                import groovy.contracts.Decreases
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class PreserveBad {
                    @Requires({ n >= 0 })
                    @Ensures({ result >= 0 })
                    static int go(int n) {
                        int i = 0
                        @Invariant({ 0 <= i && i <= n })
                        @Decreases({ n - i })
                        while (i < n) { i = i + 2 }
                        return i
                    }
                }
            '''.stripIndent()
        ],
        [
            name: "Progress: variant is i (which increases), not n - i",
            expect: "variant",
            source: '''
                import groovy.transform.TypeChecked
                import groovy.contracts.Requires
                import groovy.contracts.Ensures
                import groovy.contracts.Invariant
                import groovy.contracts.Decreases
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class ProgressBad {
                    @Requires({ n >= 0 })
                    @Ensures({ result == n })
                    static int go(int n) {
                        int i = 0
                        @Invariant({ 0 <= i && i <= n })
                        @Decreases({ i })
                        while (i < n) { i = i + 1 }
                        return i
                    }
                }
            '''.stripIndent()
        ],
        [
            name: "Use: invariant too weak to prove the postcondition after the loop",
            expect: "postcondition",
            source: '''
                import groovy.transform.TypeChecked
                import groovy.contracts.Requires
                import groovy.contracts.Ensures
                import groovy.contracts.Invariant
                import groovy.contracts.Decreases
                @TypeChecked(extensions = 'verification.VerifyChecker')
                class UseBad {
                    @Requires({ n >= 0 })
                    @Ensures({ result == n })
                    static int go(int n) {
                        int i = 0
                        @Invariant({ 0 <= i })
                        @Decreases({ n - i })
                        while (i < n) { i = i + 1 }
                        return i
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
                gcl.parseClass((String) c.source, "BadLoops_case${i + 1}.groovy")
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
        println failed == 0 ? "All bad-loop cases produced the expected diagnostics." :
                              "${failed} case(s) did not produce the expected diagnostic."
        if (failed > 0) System.exit(1)
    }
}
