package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithListWithNullsTest {
    fun withListWithNulls(x: List<Int?>): Int {
        if (x.count {it != null} > 3) {
            return 0
        }
        if (x.all { it == null} && x.size > 1) {
            return 1
        }
        if (x.all {it != null} && x.size > 1) {
            return 2
        }
        return 3
    }
}