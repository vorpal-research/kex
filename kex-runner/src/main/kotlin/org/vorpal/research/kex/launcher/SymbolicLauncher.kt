package org.vorpal.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.asm.analysis.testgen.DescriptorChecker
import org.vorpal.research.kex.asm.analysis.testgen.MethodChecker
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.CoverageReporter
import org.vorpal.research.kex.reanimator.descriptor.DescriptorStatistics
import org.vorpal.research.kex.trace.`object`.ObjectTraceManager
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Files

@ExperimentalSerializationApi
@InternalSerializationApi
class SymbolicLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {
    override fun launch() {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(context.cm)
        val useReanimator = kexConfig.getBooleanValue("reanimator", "enabled", true)

        preparePackage(context, psa)
        runPipeline(context) {
            +when {
                useReanimator -> DescriptorChecker(context, traceManager, psa)
                else -> MethodChecker(context, traceManager, psa)
            }
        }

        DescriptorStatistics.printStatistics()
        val coverage = CoverageReporter(containers).execute(context.cm, analysisLevel)
        log.info(
            coverage.print(kexConfig.getBooleanValue("kex", "printDetailedCoverage", false))
        )
        val coverageResult = kexConfig.getPathValue("kex", "outputDir", "./temp").resolve("coverage.txt")
        if (Files.notExists(coverageResult)) {
            Files.createFile(coverageResult)
        }
        Files.newBufferedWriter(coverageResult).use {
            it.write(coverage.print(kexConfig.getBooleanValue("kex", "printDetailedCoverage", false)))
        }
    }
}