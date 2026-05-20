import groovy.transform.Immutable

// `val` is the immutable binding (Kotlin/Scala-style).
// @Immutable(copyWith=true) gives lens-style nested updates without
// pulling in a lens library; Haskell's `record { addr = (addr r) { city = ... } }`
// or Scala's `p.copy(address = p.address.copy(city = "Boston"))` —
// in Groovy 6 it's a dotted path or a transactional block.

@Immutable(copyWith = true) class Address { String city, zip }
@Immutable(copyWith = true) class Person  { String name; Address address }

val alice = new Person('Alice', new Address('NYC', '10001'))

val moved = alice.copyWith('address.city': 'Boston')
assert moved.address.city == 'Boston'
assert moved.address.zip  == '10001'         // structural sharing
assert moved.name         == 'Alice'

val swapped = alice.copyWith {
    name = 'Alice2'
    address.city = old.address.city.reverse()
}
assert swapped.name == 'Alice2'
assert swapped.address.city == 'CYN'

// Destructuring covers the "pattern match the parts you care about" case.
val (name: who, age: _) = [name: 'Bob', age: 30]
assert who == 'Bob'

def (h, *t) = [1, 2, 3, 4]
assert h == 1 && t == [2, 3, 4]

println 'Immutability demo OK.'
