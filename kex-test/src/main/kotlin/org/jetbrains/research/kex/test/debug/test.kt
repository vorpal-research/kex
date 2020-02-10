@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

class BasicTests {

    open class AC

    class Impl1 : AC()

    open class Impl2 : AC()

    class Impl3 : Impl2()

    fun testAC(a: AC) {
//        if (a is Impl1) {
//            Intrinsics.assertReachable()
//        }
        if (a is Impl2) {
            Intrinsics.assertReachable()
        }
        if (a is Impl3) {
            Intrinsics.assertReachable()
        }
        val b = a as Impl1
        Intrinsics.assertReachable()
    }

}