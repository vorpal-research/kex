package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithArrayWithoutNullsTest {
    fun withArrayWithoutNulls(x: Array<Int>) {
        require(x.all { it != null && it != 30 })
    }
}