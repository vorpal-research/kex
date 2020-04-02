@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    abstract class A
    interface C

    open class A1 : A(), C
    class A2 : A()
    class A3 : A1()

    fun testTypes(a: A) {
        if (a is C) {
            if (a is A2) {
                println("unreachable")
            }
            if (a is A3) return
        }
    }
}