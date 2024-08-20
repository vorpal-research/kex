package org.vorpal.research.kex.concolic

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
class DisableNullsTest : ConcolicTest("do-not-generate-nulls") {
    init {
        RuntimeConfig.setValue("kex", "generateNulls", false)
    }
    @Test
    fun boxedTypeWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/BoxedTypeNulls"], 7.0 / 9.0)
    }
    @Test
    fun primitiveArrayWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/PrimitiveArrayNulls"], 7.0 / 9.0)
    }
    @Test
    fun boxedArrayWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/BoxedArrayNulls"], 20.0 / 24.0)
    }
    @Test
    fun listWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/ListNulls"], 20.0 / 24.0)
    }
}