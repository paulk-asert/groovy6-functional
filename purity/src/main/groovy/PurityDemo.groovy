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
import groovy.contracts.Ensures
import groovy.contracts.Modifies
import groovy.contracts.Requires
import groovy.transform.Pure
import groovy.transform.TypeChecked

// Functional programmers use the IO monad to lift effectful work into
// the type system so the compiler tracks "this method may do I/O".
// Groovy 6 takes a different route — same outcome, no wrapping.
//
//   @Pure                                        method has no observable effect
//   @Modifies({…})                               method's effects are exactly these fields
//   PurityChecker(allows: "LOGGING|METRICS|…")   graded effects, à la cats-effect IO,
//                                                configured on the checker extension
//
// PurityChecker and ModifiesChecker turn the declarations into
// compile-time guarantees rather than honour-system comments.

class Calculator {
    BigDecimal total = 0
    List<String> ledger = []

    @Pure
    @TypeChecked(extensions = 'groovy.typecheckers.PurityChecker')
    static BigDecimal vat(BigDecimal net, BigDecimal rate) {
        net * (1 + rate)            // no side effect — verified
    }

    @Requires({ amount > 0 })
    @Ensures({ total == old.total + amount })
    @Modifies({ [this.total, this.ledger] })
    @TypeChecked(extensions = 'groovy.typecheckers.ModifiesChecker')
    void post(BigDecimal amount) {
        total += amount
        ledger << "+$amount".toString()   // any other field write would be rejected
    }

    @Pure
    @TypeChecked(extensions = 'groovy.typecheckers.PurityChecker(allows: "LOGGING")')
    BigDecimal balance() {
        log.fine "balance read"     // logging tolerated via this method's allows option
        total
    }

    private static final java.util.logging.Logger log =
        java.util.logging.Logger.getLogger('Calculator')
}

var c = new Calculator()
c.post(100.0)
c.post(50.5)
assert c.balance() == 150.5
assert Calculator.vat(100.0, 0.10) == 110.0
println 'Purity demo OK.'

// The Calculator class above is a self-contained specification:
// * @Pure proves vat() is a referentially transparent function
//   — the closest thing to a Haskell `pure` value in dynamic Groovy.
// * @Modifies frames `post`: a reviewer (or an AI) knows that calling
//   post() can change only total and ledger, full stop.
// * @Requires/@Ensures is the Hoare-logic dual of Haskell's State monad:
//   the state transition is described in the contract rather than in
//   a monadic bind.
