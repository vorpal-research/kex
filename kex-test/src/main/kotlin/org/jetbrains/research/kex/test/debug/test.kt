@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug


class BasicTests {

    abstract class A {
        abstract val a: Int
    }

    class B(override val a: Int) : A()

    fun test(b: A) {
        if (b.a > 10) {
            println("aaa")
        }
        println("bbb")
    }

}