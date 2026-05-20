import groovy.transform.TypeChecked

// This file exists to demonstrate compile-time rejection.
// Toggle the @TypeChecked line on to see CombinerChecker fire:
//
//   @TypeChecked(extensions = 'groovy.typecheckers.CombinerChecker')
//   def badReduce() {
//       [1, 2, 3].injectParallel(0) { a, b -> a - b }   // non-associative
//   }
//
// In strict mode the combiner must be a method reference to an
// @Associative-annotated method:
//
//   @TypeChecked(extensions =
//     'groovy.typecheckers.CombinerChecker(strict: true)')
//   def strictReduce() {
//       [1, 2, 3].injectParallel(0) { a, b -> a + b }   // rejected: closure, not @Associative
//   }
//
// The "annotation -> compile-time guarantee -> property law" pipeline
// is what makes the next chapter (PBT derivation) possible.
println 'See comments — this file documents the rejection cases.'
