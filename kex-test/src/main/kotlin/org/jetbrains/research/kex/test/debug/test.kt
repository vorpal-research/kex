@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

class BasicTests {

    abstract class A
    interface C

    open class A1 : A(), C
    class A2 : A()
    class A3 : A1()

    fun testArray() {
        val array = arrayOf(
                arrayOf(0, 1, 2, 3, 4),
                arrayOf(5, 6, 7, 8, 9),
                arrayOf(10, 11, 12, 13, 14)
        )
        if (array[2][4] > 10) {
            Intrinsics.assertReachable()
        }
        if (array.size > 2) {
            Intrinsics.assertReachable()
        }
        if (array[0].size > 4) {
            Intrinsics.assertReachable()
        }
    }
}