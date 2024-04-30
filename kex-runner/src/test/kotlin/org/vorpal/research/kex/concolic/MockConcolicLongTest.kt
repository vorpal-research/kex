package org.vorpal.research.kex.concolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.mocking.isZeroCoverageEpsilon
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class MockConcolicLongTest : ConcolicTest("mock-concolic") {
    val prefix = "org/vorpal/research/kex/test/concolic/mock/"

    companion object {
        private fun eps(eps: Double): Double = if (kexConfig.isZeroCoverageEpsilon) 0.0 else eps
    }

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
        assertCoverage(cm[prefix + "MockWithFieldsTests"], 1.0)
    }

    @Test
    fun mockStaticsTests() {
        assertCoverage(cm[prefix + "MockStaticsTests"], 1.0)
    }

    @Test
    fun mockListTests() {
        assertCoverage(cm[prefix + "MockListTests"], 1.0, eps(0.12))
    }

    @Test
    fun mockGenericsTests() {
        assertCoverage(cm[prefix + "MockGenericsTests"], 1.0)
    }

    @Test
    fun mockSetTests() {
        assertCoverage(cm[prefix + "MockSetTests"], 1.0, eps(0.5))
    }

    @Test
    fun mockInheritanceTests() {
        // todo: enable mocking rule "any"
//        val oldMockingMode = kexConfig.mockingMode
//        RuntimeConfig.setValue("mock", "mode", "full");
        assertCoverage(cm[prefix + "MockInheritanceTests"], 1.0)
//        RuntimeConfig.setValue("mock", "mode", oldMockingMode.toString());
    }

    @Test
    fun mockLambdaTests() {
        // todo: enable mocking rule "lambda"
        assertCoverage(cm[prefix + "MockLambdaTests"], 1.0)
    }
}
