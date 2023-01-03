package org.vorpal.research.kex.symbolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class KAFSymbolicLongTest : SymbolicTest() {
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
