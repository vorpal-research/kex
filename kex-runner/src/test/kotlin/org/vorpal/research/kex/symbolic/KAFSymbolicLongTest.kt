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
class KAFSymbolicLongTest : SymbolicTest("kaf-symbolic") {
    @Test
    fun lesson2() {
        withConfigOption("testGen", "generateAssertions", "false") {
            assertCoverage(cm["org/vorpal/research/kex/test/concolic/kaf/Lesson2"], 1.0)
        }
    }

    @Test
    fun lesson6() {
        withConfigOption("testGen", "generateAssertions", "false") {
            assertCoverage(cm["org/vorpal/research/kex/test/concolic/kaf/Lesson6"], 1.0, 0.05)
        }
    }
}
