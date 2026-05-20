import groovy.contracts.Decreases
import groovy.contracts.Ensures
import groovy.contracts.Invariant
import groovy.transform.TailRecursive

// Recursion has always been safe in Groovy via @TailRecursive.
// Groovy 6 adds @Decreases and @Invariant on loops — termination and
// loop-invariant proofs that totality-checkers (Coq, Idris, Agda)
// would give an FP audience, lifted into idiomatic Groovy.

@TailRecursive
BigInteger factorial(BigInteger n, BigInteger acc = 1G) {
    n <= 1 ? acc : factorial(n - 1, n * acc)
}

assert factorial(20G) == 2_432_902_008_176_640_000G

@Ensures({ result.isSorted() })
List merge(List in1, List in2) {
    var out = []
    var count = in1.size() + in2.size()
    @Invariant({ in1.size() + in2.size() + out.size() == count })
    @Decreases({ [in1.size(), in2.size()] })
    while (in1 || in2) {
        if (!in1)      return out + in2
        if (!in2)      return out + in1
        out += (in1[0] < in2[0]) ? in1.pop() : in2.pop()
    }
    out
}

assert merge([1, 4, 7], [2, 3, 9]) == [1, 2, 3, 4, 7, 9]
println 'Termination demo OK.'
