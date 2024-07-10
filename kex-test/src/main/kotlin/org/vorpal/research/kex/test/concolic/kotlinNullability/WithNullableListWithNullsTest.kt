package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithNullableListWithNullsTest {
    fun withNullableListWithNulls(x: List<Int?>?): Int {
        if (x == null) {
            return 1
        }
        if (x.any { it == null }) {
            if (x.isNotEmpty()) {
                return 4
            }
            return 2
        }
        return 3
    }
}