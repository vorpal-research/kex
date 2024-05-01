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
class MockSymbolicLongTest : SymbolicTest("mock-symbolic") {
    private val prefix = "org/vorpal/research/kex/test/concolic/mock/"

    @Test
    fun mockTest() {
        assertCoverage(cm[prefix + "MockTests"], 1.0)
    }

    @Test
    fun mockReturnsMockTest() {
        assertCoverage(cm[prefix + "MockReturnsMockTests"], 1.0)
    }

    @Test
    fun mockPrimitivesTest() {
        assertCoverage(cm[prefix + "MockPrimitivesTests"], 1.0)
    }

    @Test
    fun mockEnumTest() {
        assertCoverage(cm[prefix + "MockEnumTests"], 1.0)
    }

    @Test
    fun mockWithFieldsTests() {
        val eps = 0.03
        assertCoverage(cm[prefix + "MockWithFieldsTests"], 1.0, eps)
    }

    @Test
    fun mockStaticsTests() {
        assertCoverage(cm[prefix + "MockStaticsTests"], 1.0, 0.0)
    }

    @Test
    fun mockListTests() {
        assertCoverage(cm[prefix + "MockListTests"], 1.0, 0.10)
    }

    @Test
    fun mockGenericsTests() {
        assertCoverage(cm[prefix + "MockGenericsTests"], 1.0)
    }

    @Test
    fun mockSetTests() {
        // unstable test. Anything can happen
        assertCoverage(cm[prefix + "MockSetTests"], 1.0, 0.5)
    }
}
