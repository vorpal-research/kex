package org.vorpal.research.kex.concolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.KexRunnerTest
import org.vorpal.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.jacoco.CoverageReporter
import org.vorpal.research.kex.launcher.ClassLevel
import org.vorpal.research.kex.trace.runner.ExecutorMasterController
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.visitor.executePackagePipeline
import org.vorpal.research.kthelper.logging.log
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
abstract class ConcolicTest(testDirectoryName: String) : KexRunnerTest(testDirectoryName) {

    fun assertCoverage(klass: Class, expectedCoverage: Double = 1.0, eps: Double = 0.0) {
        ExecutorMasterController.use {
            it.start(analysisContext)
            executePackagePipeline(analysisContext.cm, Package.defaultPackage) {
                +ClassInstantiationDetector(analysisContext)
            }

            for (method in klass.allMethods.sortedBy { method -> method.prototype }) {
                InstructionConcolicChecker.run(analysisContext, setOf(method))
            }

            val coverage = CoverageReporter(klass.cm, listOf(jar)).computeCoverage(ClassLevel(klass))
            log.debug(coverage.first().print(true))
            assertEquals(expectedCoverage, coverage.first().instructionCoverage.ratio, eps)
        }
    }
}
