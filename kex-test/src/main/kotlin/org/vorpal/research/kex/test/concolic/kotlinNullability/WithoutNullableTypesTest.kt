package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithoutNullableTypesTest {
    fun withoutNullableTypes(x: Int): Int {
        if (x == 239) {
            error("It's a very bad number")
        }
        return x + 566
    }
}