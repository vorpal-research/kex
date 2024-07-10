package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithArrayWithNullsTest {
    fun withArrayWithNulls(x: Array<Int?>) {
        require(x.any { it != null })
    }
}