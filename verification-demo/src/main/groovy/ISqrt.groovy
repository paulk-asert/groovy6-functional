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
import verification.Requires

/**
 * The classic Dafny example, ported to Groovy: an integer
 * square-root with a precondition that the input is non-negative.
 *
 * On its own, @Requires is just documentation. Under
 *   @TypeChecked(extensions = 'verification.VerifyChecker')
 * it becomes an enforced contract: every call site must PROVE that
 * x >= 0 holds — by literal value, by an enclosing condition, or by
 * facts visible to the checker's encoder. Z3 discharges the proof
 * obligation at the consumer's compile time.
 *
 * Note: the producer is intentionally NOT @CompileStatic. The
 * contract closure references the method's formals by name, and the
 * stock static type checker doesn't know to resolve those names
 * against the enclosing method's parameter list. The contract closure
 * is for the verifier, not the runtime, so plain Groovy here is the
 * expedient choice; the call-site checking still happens under the
 * consumer's @TypeChecked(extensions = '...VerifyChecker').
 */
class ISqrt {
    @Requires({ x >= 0 })
    static int isqrt(int x) {
        (int) Math.sqrt((double) x)
    }
}
