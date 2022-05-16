package org.vorpal.research.kex.concolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class StringLongTest : ConcolicTest() {
    @Test
    fun primitiveConcolicTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/StringConcolicTests"], 1.0)
    }
}
