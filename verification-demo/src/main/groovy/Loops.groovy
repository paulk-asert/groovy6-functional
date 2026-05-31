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
import groovy.contracts.Decreases
import groovy.contracts.Ensures
import groovy.contracts.Invariant
import groovy.contracts.Requires

/**
 * Loop verification — the classical inductive proof. Each `while` carries a
 * stock `@groovy.contracts.Invariant` and `@Decreases`; the checker discharges
 * four obligations per loop (establishment, preservation, use, progress) via Z3
 * at compile time, and groovy-contracts independently emits the runtime
 * invariant/variant checks.
 *
 * Everything here stays in linear integer arithmetic (no `i * (i+1)`): the
 * encoder's QF_LIA fragment is the scope for this phase.
 *
 * Run with:
 *   ../gradlew :verification-demo:run.Loops
 */
@TypeChecked(extensions = 'verification.VerifyChecker')
class Loops {

    /** Count up to n. The invariant pins i to [0, n]; the use obligation gives result == n. */
    @Requires({ n >= 0 })
    @Ensures({ result == n })
    static int countUp(int n) {
        int i = 0
        @Invariant({ 0 <= i && i <= n })
        @Decreases({ n - i })
        while (i < n) {
            i = i + 1
        }
        return i
    }

    /** Accumulate a non-negative running total; the invariant carries sum >= 0 through the loop. */
    @Requires({ n >= 0 })
    @Ensures({ result >= 0 })
    static int sumTo(int n) {
        int sum = 0
        int i = 0
        @Invariant({ 0 <= i && i <= n && sum >= 0 })
        @Decreases({ n - i })
        while (i < n) {
            i = i + 1
            sum = sum + i
        }
        return sum
    }

    static void main(String[] args) {
        assert countUp(0) == 0
        assert countUp(5) == 5
        assert sumTo(0) == 0
        assert sumTo(4) == 10
        println "All Loops postconditions and invariants verified at compile time."
    }
}
