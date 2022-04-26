package org.jetbrains.research.kex.concolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.KexRunnerTest
import org.jetbrains.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.jetbrains.research.kex.asm.manager.ClassInstantiationDetector
import org.jetbrains.research.kex.asm.transform.SymbolicTraceCollector
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.jacoco.CoverageReporter
import org.jetbrains.research.kex.launcher.ClassLevel
import org.jetbrains.research.kex.trace.symbolic.InstructionTraceManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.logging.log
import java.net.URLClassLoader
import kotlin.test.assertEquals

@ExperimentalSerializationApi
@InternalSerializationApi
abstract class ConcolicTest : KexRunnerTest() {

    override fun createTraceCollector(context: ExecutionContext) = SymbolicTraceCollector(context)

    fun assertCoverage(klass: Class, expectedCoverage: Double = 1.0) {
        val traceManager = InstructionTraceManager()
        executePipeline(analysisContext.cm, Package.defaultPackage) {
            +ClassInstantiationDetector(analysisContext.cm, Visibility.PRIVATE)
        }
        executePipeline(analysisContext.cm, klass) {
            +InstructionConcolicChecker(analysisContext, traceManager)
        }
        val coverage = CoverageReporter(jar.classLoader as URLClassLoader).execute(klass.cm, ClassLevel(klass))
        log.debug(coverage.print(true))
        assertEquals(expectedCoverage, coverage.instructionCoverage.ratio)
    }
}