@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE")

package org.jetbrains.research.kex.test.debug

class ObjectTests {
    data class Point(val x: Int, val y: Int, val z: Int)
}

object Intrinsics {
    @JvmStatic
    fun assertReachable(vararg conditions: Boolean) {}

    @JvmStatic
    fun assertUnreachable() {}
}