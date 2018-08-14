package org.jetbrains.research.kex.test

object Intrinsics {
    @JvmStatic
    fun assertReachable(vararg conditions: Boolean) {}

    @JvmStatic
    fun assertUnreachable() {}
}