package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithNullableArrayTest {
    fun withNullableArray(x: Array<Int>?) {
        require(x == null)
    }
}