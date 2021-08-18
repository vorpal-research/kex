@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    class A {
        companion object {
            @JvmStatic
            val a = intArrayOf(
                3, 4, 5, 6, 7, 8, 9
            )

            @JvmStatic
            fun getArrayVal(index: Int): Int {
                return when (index) {
                    0 -> a[1]
                    1 -> a[4]
                    5 -> a[7]
                    2 -> a[2]
                    else -> a[0]
                }
            }
        }

    }

    fun test(a: Int): Int {
        val value = A.getArrayVal(a)
        if (value > 3) {
            return 1
        }
        return 0
    }
}