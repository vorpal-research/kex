package org.vorpal.research.kex.test.concolic.kotlinNullability

class WithNullableTypesTest {
    fun withNullableTypes(x: Int?): Double {
        val y = x ?: 2024
        return 1.0 / y
    }
}