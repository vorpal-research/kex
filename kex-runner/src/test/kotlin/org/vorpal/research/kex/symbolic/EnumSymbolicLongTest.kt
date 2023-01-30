package org.vorpal.research.kex.symbolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test
import org.vorpal.research.kex.config.RuntimeConfig
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class EnumSymbolicLongTest : SymbolicTest("enum-symbolic") {
    @Test
    fun enumTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/EnumConcolicTests"], 1.0)
    }
}
