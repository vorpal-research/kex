@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    fun digitNumber(n: Int): Int =
            if (n in -9..9) 1
            else digitNumber(n / 10) + digitNumber(n % 10)
}