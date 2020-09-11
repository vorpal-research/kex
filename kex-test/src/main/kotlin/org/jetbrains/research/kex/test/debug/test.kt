@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    class RecursiveTest {
        constructor(rt: RecursiveTest)
        constructor(a: Int, b: Int)
    }

}