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
class AssertAndExceptionsTest : ConcolicTest("assert-and-exceptions")  {
    @Test
    fun assertAndExceptionsTest1() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/AssertAndExceptionsTests1"], 1.0)
    }

    @Test
    fun assertAndExceptionsTest2() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/AssertAndExceptionsTests2"], 1.0)
    }
}