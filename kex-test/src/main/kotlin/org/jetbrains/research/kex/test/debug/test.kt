@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    open class A {
        open fun value() = 10
    }

    class B(val a: Int) : A() {
        override fun value(): Int = a
    }

    class W(val a: A)

    fun test(w: W) {
        if (w.a is B) {
            if (w.a.value() > 10) {
                println("a")
            }
        }
    }
}