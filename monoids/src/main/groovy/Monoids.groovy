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
import groovy.transform.Associative
import groovy.transform.Reducer
import groovy.transform.TypeChecked

// A semigroup is "associative combine".
// A monoid adds an identity element ("zero").
// In Haskell:           class Semigroup a where <> :: a -> a -> a
//                       class Semigroup a => Monoid a where mempty :: a
// In functionaljava:    fj.Semigroup<A>, fj.Monoid<A> — built by combinator
// In highj:             org.highj.algebra.monoid.Monoid<A>
// In Groovy 6:          @Associative declares an associative binary op (semigroup);
//                       @Reducer is the monoid form — associative plus a named
//                       identity via zero(), so it implies @Associative;
//                       CombinerChecker enforces the contract at compile time.

class Sum {
    @Reducer(zero = '0')
    static int add(int a, int b) { a + b }
}

class Concat {
    @Reducer(zero = '""')
    static String join(String a, String b) { a + b }
}

// A bag-of-counts forms a monoid under per-key addition.
// Same algebra as Haskell's `Map k (Sum Int)` Monoid instance,
// or functionaljava's `Monoid.mapMonoid(Monoid.intAdditionMonoid)`.
class Tally {
    @Reducer(zero = '[:]')
    static Map<String, Integer> merge(Map<String, Integer> a, Map<String, Integer> b) {
        var out = new LinkedHashMap(a)
        b.each { k, v -> out.merge(k, v) { x, y -> x + y } }
        out
    }
}

// A semigroup that is not a monoid: max on int has no natural identity
// in the problem domain (Integer.MIN_VALUE is a sentinel, not a real zero).
// Usable with the unseeded reduction only.
class Largest {
    @Associative
    static int max(int a, int b) { a >= b ? a : b }
}

@TypeChecked(extensions = 'groovy.typecheckers.CombinerChecker')
def reductions() {
    assert (1..100).toList().injectParallel(0, Sum.&add) == 5050
    assert ['a', 'b', 'c'].sumParallel(Concat.&join) == 'abc'
    assert [3, 1, 4, 1, 5, 9, 2, 6].sumParallel(Largest.&max) == 9

    def words = 'the quick brown fox jumps over the lazy dog'.split(/\s+/)
    var counts = words.collect { [(it): 1] as Map<String, Integer> }
                      .injectParallel([:], Tally.&merge)
    assert counts.values().sum() == 9
    assert counts['the'] == 2

    // The next line would be REJECTED at compile time by CombinerChecker
    // because subtraction is not associative:
    //   [1, 2, 3].injectParallel(0) { a, b -> a - b }
}

reductions()
println 'Monoid demo OK.'
