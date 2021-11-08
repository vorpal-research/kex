package org.jetbrains.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.analysis.testgen.DescriptorChecker
import org.jetbrains.research.kex.asm.analysis.testgen.MethodChecker
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.SymbolicTraceCollector
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.jacoco.CoverageLevel
import org.jetbrains.research.kex.jacoco.CoverageReporter
import org.jetbrains.research.kex.reanimator.descriptor.DescriptorStatistics
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.logging.log
import java.util.*

@ExperimentalSerializationApi
@InternalSerializationApi
class SymbolicLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {
    override fun launch() {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(context.cm)
        val useApiGeneration = kexConfig.getBooleanValue("apiGeneration", "enabled", true)

        preparePackage(context, psa)
        runPipeline(context) {
            +when {
                useApiGeneration -> DescriptorChecker(context, traceManager, psa)
                else -> MethodChecker(context, traceManager, psa)
            }
        }

        DescriptorStatistics.printStatistics()
        log.info(
            CoverageReporter(pkg, containerClassLoader)
                .execute(
                    CoverageLevel.PackageLevel(printDetailedCoverage = false)
                )
        )
    }
}