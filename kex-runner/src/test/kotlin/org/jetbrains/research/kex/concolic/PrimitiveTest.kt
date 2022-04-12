package org.jetbrains.research.kex.concolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class PrimitiveTest : ConcolicTest() {
    @Test
    fun primitiveConcolicTest() {
        assertCoverage(cm["org/jetbrains/research/kex/test/concolic/PrimitiveConcolicTests"], 1.0)
    }
}