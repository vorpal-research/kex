package org.vorpal.research.kex.crash

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test
import org.vorpal.research.kex.asm.analysis.crash.CrashReproductionChecker
import org.vorpal.research.kex.test.crash.CrashTrigger
import kotlin.test.Ignore
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class ConstraintCrashReproductionLongTest : CrashReproductionTest(
    "constraint-crash-reproduction",
    CrashReproductionChecker::runWithConstraintPreconditions
) {

    @Test
    fun testNullPointerException() {
        val expectedStackTrace = produceStackTrace { CrashTrigger().triggerNullPtr() }
        assertCrash(expectedStackTrace)
    }

//    @Test
//    fun testAssertionError() {
//        val expectedStackTrace = produceStackTrace { CrashTrigger().triggerAssert() }
//        assertCrash(expectedStackTrace)
//    }

    @Ignore
    @Test
    fun testArithmeticException() {
        val expectedStackTrace = produceStackTrace { CrashTrigger().triggerException() }
        assertCrash(expectedStackTrace)
    }

    @Test
    fun testNegativeSizeArrayException() {
        val expectedStackTrace = produceStackTrace { CrashTrigger().triggerNegativeArray() }
        assertCrash(expectedStackTrace)
    }

    @Test
    fun testArrayIndexOOBException() {
        val expectedStackTrace = produceStackTrace { CrashTrigger().triggerArrayOOB() }
        assertCrash(expectedStackTrace)
    }
}

