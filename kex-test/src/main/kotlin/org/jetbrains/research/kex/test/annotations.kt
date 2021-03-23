@file:Suppress("unused", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.Intrinsics.kexAssert
import org.jetbrains.research.kex.Intrinsics.kexUnreachable
import java.io.ByteArrayInputStream

object AnnotatedMethodsThere {
    @JvmStatic
    fun rangeExample(param0: Any?, param1: Int): Int {
        return param1 + 2
    }
    @JvmStatic
    // "!null, _ -> !null; _, true -> new; _, false -> param1"
    fun haveContract(param0: Any?, param1: Boolean): Any? = if (param1) Any() else param0

    // "false -> fail; _ -> this"
    fun assertTrue(param0: Boolean): AnnotatedMethodsThere {
        if (!param0) throw IllegalStateException()
        return this
    }

    fun assertNotNull(param0: Any?) {
        if (param0 == null) throw IllegalArgumentException()
    }

    fun makeBeautifulList(n: Int) = MutableList(n) { 0 }
}

class NotAnnotatedMethods {
    fun test1(): Int {
        val n = AnnotatedMethodsThere.rangeExample(Any(), 500)
        if (n < 0 || n > Int.MAX_VALUE)
            kexUnreachable()
        else
            kexAssert()
        return n
    }

    fun test2(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        if (!(arg1 == null || result != null))
            kexUnreachable()
        return result
    }

    fun test3(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        if (arg0 && result === null)
            kexUnreachable()
        return result
    }

    fun test4(arg0: Any?): Any? {
        val result = AnnotatedMethodsThere.rangeExample(arg0, 500)
        if (result < 0)
            kexUnreachable()
        else
            kexAssert()
        if (arg0 == null)
            kexUnreachable()
        return result
    }

    fun test5(arg0: Boolean): Any? {
        val o = AnnotatedMethodsThere
        val result = o.assertTrue(arg0)
        if (!arg0)
            kexUnreachable()
        if (result !== o)
            kexUnreachable()
        else
            kexAssert()
        return o
    }

    fun test6(arg0: Boolean, arg1: Any?): Any? {
        val result = AnnotatedMethodsThere.haveContract(arg1, arg0)
        if (!arg0 && result !== arg1)
            kexUnreachable()
        return result
    }
}

class ThatClassContainsHighQualityCodeToProf {
    fun incredibleMethod(exitingArgument: String, unusualNumber: Double): List<Int> {
        val importantMethods = AnnotatedMethodsThere
        val lovelyInteger = unusualNumber.toInt()
        if (lovelyInteger.toDouble() == unusualNumber) {
            val bestStream = ByteArrayInputStream(exitingArgument.toByteArray())
            val meaningfulResult = importantMethods.makeBeautifulList(lovelyInteger)
            //while (bestStream.available() > 0) {
                val remarkableLetter = bestStream.read()
                importantMethods.assertTrue(importantMethods.assertTrue(remarkableLetter > 0) == importantMethods)
                meaningfulResult += remarkableLetter
            //}
            importantMethods.assertNotNull(meaningfulResult)
            return meaningfulResult
        }
        return emptyList()
    }
}
