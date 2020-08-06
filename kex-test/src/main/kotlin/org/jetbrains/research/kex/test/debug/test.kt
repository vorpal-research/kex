@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

class BasicTests {

    abstract class AbstractClass(val x: Int, val y: Int)

    class Impl(x: Int, y: Int, val z: Int) : AbstractClass(x, y)

    class SecondImpl(x: Int, y: Int) : AbstractClass(x, y)

    class ThirdImpl(x: Int, y: Int, val inner: AbstractClass) : AbstractClass(x, y)

    fun testAbstractClass(klass: AbstractClass) {
        if (klass.x > klass.y) {
            Intrinsics.assertReachable()
        }
        if (klass is SecondImpl) {
            Intrinsics.assertReachable()
        } else if (klass is ThirdImpl) {
            Intrinsics.assertReachable()
        }
    }

}