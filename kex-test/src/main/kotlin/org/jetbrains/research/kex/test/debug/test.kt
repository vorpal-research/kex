@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics
import org.jetbrains.research.kex.test.generation.BasicGenerationTests

class BasicTests {

    fun testArray(array: Array<BasicGenerationTests.Point>) {
        if (array[0].x > 0) {
            Intrinsics.assertReachable()
        }
        if (array[1].y < 0) {
            Intrinsics.assertReachable()
        }
        Intrinsics.assertReachable()
    }
}