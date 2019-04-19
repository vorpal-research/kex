@file:Suppress("unused", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.test.Intrinsics.assertReachable
import org.jetbrains.research.kex.test.Intrinsics.assertUnreachable

object AnnotatedMethodsThere {
    @JvmStatic
    fun rangeExample(param0: Any?, param1: Int): Int {
        return param1 + 2
    }
    @JvmStatic
    fun haveContract(param0: Any?, param1: Boolean): Any? = if (param1) param0 ?: Any() else param0

    fun assertTrue(param0: Boolean): AnnotatedMethodsThere {
        if (!param0) throw IllegalStateException()
        return this
    }
}

class NotAnnotatedMethods {
    fun test1(): Int {
        val n = AnnotatedMethodsThere.rangeExample(Any(), 500)
        if (n < 0 || n > Int.MAX_VALUE)
            assertUnreachable()
        else
            assertReachable()
        return n
    }
    fun test2(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        if (arg1 == null || result != null)
            assertReachable()
        else
            assertUnreachable()
        return result
    }
    fun test3(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        if (arg0 && result === null)
            assertUnreachable()
        return result
    }
    fun test4(arg0: Any?): Any? {
        val result = AnnotatedMethodsThere.rangeExample(arg0, 500)
        if (result < 0)
            assertUnreachable()
        else
            assertReachable()
        if (arg0 == null)
            assertUnreachable()
        return result
    }
    fun test5(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        if (arg0 && result !== arg1)
            assertUnreachable()
        return result
    }
    fun test6(arg0: Boolean): Any? {
        val o = AnnotatedMethodsThere
        val result = o.assertTrue(arg0)
        if (!arg0)
            assertUnreachable()
        if (result === o)
            assertReachable()
        else
            assertUnreachable()
        return o
    }
}

class ClassWithContractOffense {
    fun test1(arg0: Any?): Any? {
        val result = AnnotatedMethodsThere.rangeExample(arg0, -500)
        assertReachable()
        return result
    }
}
