@file:Suppress("unused", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test

object AnnotatedMethodsThere {
    @JvmStatic
    fun rangeExample(param0: Any?, param1: Int): Int {
        return param1 + 2
    }
    @JvmStatic
    fun haveContract(param0: Any?, param1: Boolean): Any? = if (param1) param0 ?: Any() else param0
}

class NotAnnotatedMethods {
    fun test1(): Int {
        val n = AnnotatedMethodsThere.rangeExample(Any(), -500)
        Intrinsics.assertReachable()
        return n
    }
    fun test2(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        Intrinsics.assertReachable()
        return result
    }
}
