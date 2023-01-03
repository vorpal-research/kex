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
class ListConcolicLongTest : ConcolicTest() {
    @Test
    fun listConcolicTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/ListConcolicTests"], 1.0)
    }
}
