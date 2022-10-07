package org.vorpal.research.kex.concolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Ignore
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class KAFLongTest : ConcolicTest() {
    @Test
    fun lesson2() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/kaf/Lesson2"], 1.0)
    }

    @Test
    fun lesson6() {
        // TODO: investigate test failure (with only 99% coverage) on CI
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/kaf/Lesson6"], 1.0)
    }
}
