package org.vorpal.research.kex.concolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class EnumConcolicLongTest : ConcolicTest() {
    @Test
    fun enumTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/EnumConcolicTests"], 1.0)
    }
}
