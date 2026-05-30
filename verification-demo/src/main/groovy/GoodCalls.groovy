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

/**
 * Call sites that VerifyChecker PROVES are safe. Compilation succeeds
 * only because Z3 discharges the precondition x >= 0 at each call.
 *
 * Note: this class uses @TypeChecked WITHOUT @CompileStatic.
 * @CompileStatic disables the extension-hook dispatch — the extension
 * is set up but its onMethodSelection/beforeVisitMethod callbacks
 * don't fire. This matches the wire-checker convention.
 *
 * Run with:
 *   ../gradlew :verification-demo:run.GoodCalls
 */
@TypeChecked(extensions = 'verification.VerifyChecker')
class GoodCalls {

    /** Literal argument that obviously satisfies the precondition. */
    static int viaLiteral() {
        ISqrt.isqrt(5)
        // Z3 solves: ¬(5 >= 0) is UNSAT → verified.
    }

    /** Symbolic argument constrained by the enclosing 'if'. */
    static int viaIfCondition(int n) {
        if (n >= 0) {
            return ISqrt.isqrt(n)
            // Path condition n >= 0 plus ¬(n >= 0) is UNSAT → verified.
        }
        -1
    }

    /** Stronger enclosing condition: n > 0 implies n >= 0. */
    static int viaStrongerCondition(int n) {
        if (n > 0) {
            return ISqrt.isqrt(n)
            // Path condition n > 0 plus ¬(n >= 0) is UNSAT → verified.
        }
        0
    }

    /**
     * Arithmetic in the argument expression itself. The checker
     * encodes the actual argument and the enclosing guard — it does
     * not (yet) track local-variable assignments, so the arithmetic
     * goes directly in the call, not via an intermediate `int squared`.
     */
    static int viaArithmetic(int k) {
        if (k > 0) {
            return ISqrt.isqrt(k * 1)   // multiplication by a literal stays linear
            // Solver proves k > 0 ⇒ k*1 >= 0 → verified.
        }
        0
    }

    /** Negation in the else branch. */
    static int viaElseNegation(int n) {
        if (n < 0) {
            return -1
        } else {
            // Path condition: ¬(n < 0), i.e. n >= 0.
            return ISqrt.isqrt(n)
        }
    }

    static void main(String[] args) {
        assert viaLiteral() == 2  // sqrt(5) == 2.236..., truncated
        assert viaIfCondition(9) == 3
        assert viaIfCondition(-1) == -1
        assert viaStrongerCondition(16) == 4
        assert viaArithmetic(4) == 2
        assert viaElseNegation(25) == 5
        println "All GoodCalls assertions passed — and every isqrt call was proven safe at compile time."
    }
}
