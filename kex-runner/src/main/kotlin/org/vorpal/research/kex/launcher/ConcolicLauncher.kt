package org.vorpal.research.kex.launcher

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.vorpal.research.kex.asm.transform.SymbolicTraceInstrumenter
import org.vorpal.research.kex.asm.util.ClassWriter
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.CoverageReporter
import org.vorpal.research.kex.trace.runner.ExecutorMasterController
import org.vorpal.research.kex.util.PermanentCoverageInfo
import org.vorpal.research.kex.util.PermanentSaturationCoverageInfo
import org.vorpal.research.kex.util.instrumentedCodeDirectory
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kthelper.logging.log
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class ConcolicLauncher(classPaths: List<String>, targetName: String) : KexAnalysisLauncher(classPaths, targetName) {
    override fun prepareClassPath(ctx: ExecutionContext): Pipeline.() -> Unit = {
        +SymbolicTraceInstrumenter(ctx)
        +ClassWriter(ctx, kexConfig.instrumentedCodeDirectory)
    }

    private val batchedTargets: Set<Set<Method>>
        get() = when (analysisLevel) {
            is ClassLevel -> setOf(analysisLevel.klass.allMethods)
            is MethodLevel -> setOf(setOf(analysisLevel.method))
            is PackageLevel -> context.cm.getByPackage(analysisLevel.pkg).mapTo(mutableSetOf()) { it.allMethods }
        }

    override fun launch() {
        ExecutorMasterController.use {
            it.start(context)

            for (setOfTargets in batchedTargets) {
                InstructionConcolicChecker.run(context, setOfTargets)
            }
        }

        if (kexConfig.getBooleanValue("kex", "computeCoverage", true)) {
            val coverageInfo = when {
                kexConfig.getBooleanValue("kex", "computeSaturationCoverage", true) -> {
                    val saturationCoverage = CoverageReporter(containers)
                        .computeSaturationCoverage(context.cm, analysisLevel)
                    PermanentSaturationCoverageInfo.putNewInfo(
                        "concolic",
                        analysisLevel.toString(),
                        saturationCoverage.toList()
                    )
                    PermanentSaturationCoverageInfo.emit()
                    saturationCoverage[saturationCoverage.lastKey()]!!
                }

                else -> CoverageReporter(containers).execute(context.cm, analysisLevel)
            }

            log.info(
                coverageInfo.print(kexConfig.getBooleanValue("kex", "printDetailedCoverage", false))
            )

            PermanentCoverageInfo.putNewInfo("concolic", analysisLevel.toString(), coverageInfo)
            PermanentCoverageInfo.emit()
        }
    }
}
