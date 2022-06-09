package org.vorpal.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asm.transform.SymbolicTraceCollector
import org.vorpal.research.kex.asm.transform.SystemExitTransformer
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.CoverageReporter
import org.vorpal.research.kex.trace.runner.ExecutorMasterController
import org.vorpal.research.kex.util.PermanentCoverageInfo
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log

@ExperimentalSerializationApi
@InternalSerializationApi
class ConcolicLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {
    override fun createInstrumenter(context: ExecutionContext): MethodVisitor {
        return SymbolicTraceCollector(context)
    }

    override fun preparePackage(ctx: ExecutionContext, psa: PredicateStateAnalysis, pkg: Package) =
        executePipeline(ctx.cm, pkg) {
            +ClassInstantiationDetector(ctx.cm, visibilityLevel)
        }

    private val targetMethods: Set<Method>
        get() = when (analysisLevel) {
            is ClassLevel -> analysisLevel.klass.allMethods
            is MethodLevel -> setOf(analysisLevel.method)
            is PackageLevel -> context.cm.getByPackage(analysisLevel.pkg).flatMap { it.allMethods }.toSet()
        }

    override fun launch() {
        ExecutorMasterController.use {
            it.start(context)

            runPipeline(context) {
                +SystemExitTransformer(context.cm)
            }

            InstructionConcolicChecker.run(context, targetMethods)

            val coverageInfo = CoverageReporter(containers).execute(context.cm, analysisLevel)
            log.info(
                coverageInfo.print(kexConfig.getBooleanValue("kex", "printDetailedCoverage", false))
            )

            PermanentCoverageInfo.putNewInfo(analysisLevel.toString(), coverageInfo)
            PermanentCoverageInfo.emit()
        }
    }
}