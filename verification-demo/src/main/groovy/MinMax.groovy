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
import groovy.transform.TypeChecked
import groovy.contracts.Ensures

/**
 * Postconditions (`@Ensures`) verified against the method body — the
 * checker reasons about what a method *returns*, not just what its
 * callers pass in. Each method below compiles only because Z3 proves
 * the postcondition on every execution path of the body.
 *
 * Unlike the `@Requires` demos, the class carrying `@Ensures` is itself
 * `@TypeChecked(extensions = '…')`: a postcondition is checked where
 * the method is defined, not where it is called.
 *
 * These are stock `@groovy.contracts.Ensures` annotations, so groovy-contracts
 * also generates a runtime postcondition check (including on the static
 * methods here); the Z3 pass discharges the same contract at compile time.
 *
 * Run with:
 *   ../gradlew :verification-demo:run.MinMax
 */
@TypeChecked(extensions = 'verification.VerifyChecker')
class MinMax {

    /** Two return paths; the postcondition holds on both. */
    @Ensures({ result >= a && result >= b })
    static int max(int a, int b) {
        if (a > b) a else b
    }

    /** Negation in one branch; absolute value is non-negative. */
    @Ensures({ result >= 0 })
    static int abs(int x) {
        if (x < 0) -x else x
    }

    /**
     * Straight-line body with a single-assignment local. The checker
     * threads `y == x + 1` as a path fact, so it proves the result.
     */
    @Ensures({ result == x + 1 })
    static int inc(int x) {
        int y = x + 1
        y
    }

    static void main(String[] args) {
        assert max(3, 5) == 5
        assert max(9, 2) == 9
        assert abs(-7) == 7
        assert abs(4) == 4
        assert inc(41) == 42
        println "All MinMax postconditions verified at compile time."
    }
}
