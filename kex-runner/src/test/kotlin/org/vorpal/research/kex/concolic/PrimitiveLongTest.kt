package org.vorpal.research.kex.concolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class PrimitiveLongTest : ConcolicTest() {
    @Test
    fun primitiveConcolicTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/PrimitiveConcolicTests"], 1.0)
    }
}