@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    abstract class A
    abstract class B
    interface C

    open class A1 : A(), C
    class A2 : A()
    class A3 : A1()

    class B1 : B()
    class B2 : B(), C

    fun testTypes(a: A, b: B) {
        if (a is A1) {
            if (a is A3) {
                println("A3")
            }
            println("A1")
        }
        if (a is A2) {
            println("A2")
        }

        if (b is B1) {
            println("B1")
        }
        if (b is B2) {
            println("B2")
        }
    }

}