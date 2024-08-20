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
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/BoxedTypeNulls"], 7.0 / 9.0, eps = 0.1)
    }
    @Test
    fun primitiveArrayWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/PrimitiveArrayNulls"], 7.0 / 9.0, eps = 0.1)
    }
    @Test
    fun boxedArrayWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/BoxedArrayNulls"], 26.0 / 30.0, eps = 0.1)
    }
    @Test
    fun listWithoutNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/ListNulls"], 26.0 / 30.0, eps = 0.1)
    }
}
