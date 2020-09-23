@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import kotlin.system.exitProcess

class BasicTests {

    fun test(a: Int, b: Int) {
        if (a > b) {
            exitProcess(1)
        }
    }

}