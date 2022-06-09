package org.vorpal.research.kex.concolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class KAFLongTest : ConcolicTest() {
    @Test
    fun lesson2() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/kaf/Lesson2"], 1.0)
    }

    @Test
    fun lesson6() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/kaf/Lesson6"], 1.0)
    }
}