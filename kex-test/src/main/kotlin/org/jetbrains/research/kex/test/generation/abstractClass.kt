package org.jetbrains.research.kex.test.generation

import org.jetbrains.research.kex.test.Intrinsics

class AbstractClassTests {
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


    interface Interface {
        val value: Int
    }

    class InterfaceImpl(override val value: Int) : Interface
    class ACAndInterface(x: Int) : AbstractClass(x, -43), Interface {
        override val value: Int
            get() = x
    }

    fun testInterface(interfaceInstance: Interface) {
        if (interfaceInstance.value > 50) {
            Intrinsics.assertReachable()
            if (interfaceInstance is ACAndInterface) {
                Intrinsics.assertReachable()
            }
        } else if (interfaceInstance is InterfaceImpl) {
            Intrinsics.assertReachable()
        } else {
            Intrinsics.assertReachable()
        }
    }
}