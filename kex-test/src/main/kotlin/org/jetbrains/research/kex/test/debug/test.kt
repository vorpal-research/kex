@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug


class BasicTests {

    abstract class A {
        abstract val isEmpty: Boolean
    }
    class B(override val isEmpty: Boolean) : A()

    class Test(val a: A)

    fun test(t: Test) {
        if (t.a.isEmpty) {
            println("aaa")
        }
    }

}