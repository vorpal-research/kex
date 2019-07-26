@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.AnnotatedMethodsThere
import java.io.ByteArrayInputStream

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