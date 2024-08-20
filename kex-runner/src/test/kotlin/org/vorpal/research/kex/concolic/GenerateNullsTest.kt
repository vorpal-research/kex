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
class GenerateNullsTest : ConcolicTest("generate-nulls") {
    init {
        RuntimeConfig.setValue("kex", "generateNulls", true)
    }
    @Test
    fun boxedTypeWithNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/BoxedTypeNulls"], 1.0, eps = 0.1)
    }
    @Test
    fun primitiveArrayWithNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/PrimitiveArrayNulls"], 1.0, eps = 0.1)
    }
    @Test
    fun boxedArrayWithNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/BoxedArrayNulls"], 1.0, eps = 0.1)
    }
    @Test
    fun listWithNullsTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/nullability/ListNulls"], 1.0, eps = 0.1)
    }
}
