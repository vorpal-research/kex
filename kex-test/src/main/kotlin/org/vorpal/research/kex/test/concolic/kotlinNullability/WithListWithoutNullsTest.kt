package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithListWithoutNullsTest {
    fun withListWithoutNulls(x: List<Int>): Int {
        if (x.isEmpty()) {
            error("empty")
        }
        if (x.first() == 10) {
            return 1
        }
        if (x.size == 15) {
            return 2
        }
        return 3
    }
}