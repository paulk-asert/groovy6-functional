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
import groovy.contracts.Requires

/**
 * The classic Dafny example, ported to Groovy: an integer
 * square-root with a precondition that the input is non-negative.
 *
 * {@code @Requires(\{ x >= 0 \})} is a stock groovy-contracts precondition,
 * so groovy-contracts generates the usual runtime check. Layered on top,
 * compiling a caller under
 *   @TypeChecked(extensions = 'verification.VerifyChecker')
 * turns it into a statically enforced contract: every call site must
 * PROVE that x >= 0 holds — by literal value, by an enclosing condition,
 * or by facts visible to the checker's encoder — and Z3 discharges the
 * proof obligation at the caller's compile time, before the runtime check
 * would ever have a chance to fire.
 *
 * The verifier needs the contract's source text, which the built-in
 * annotation doesn't retain. {@code ContractExpansionTransform} captures it
 * verbatim into a {@code @ContractSource} that survives into bytecode, so
 * the proof works even when the caller lives in a different module.
 */
class ISqrt {
    @Requires({ x >= 0 })
    static int isqrt(int x) {
        (int) Math.sqrt((double) x)
    }
}
