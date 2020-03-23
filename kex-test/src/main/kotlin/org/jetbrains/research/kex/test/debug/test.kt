@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import java.io.ByteArrayInputStream

class BasicTests {
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

}