package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithNullableArrayWithNullableTypes {
    fun withNullableArrayWithNullableTypes(x: Array<Int?>?): Int {
        if (x == null) {
            return 1
        }
        if (x.any { it == null }) {
            if (x.all { it == null }) {
                return 2
            }
            return 3
        }
        return 4
    }
}