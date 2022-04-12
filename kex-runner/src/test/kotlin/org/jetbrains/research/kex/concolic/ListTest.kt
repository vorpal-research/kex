package org.jetbrains.research.kex.concolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
class ListTest : ConcolicTest() {
    @Test
    fun primitiveConcolicTest() {
        assertCoverage(cm["org/jetbrains/research/kex/test/concolic/ListConcolicTests"], 1.0)
    }
}