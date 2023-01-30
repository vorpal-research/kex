package org.vorpal.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.defect.DefectChecker
import org.vorpal.research.kex.asm.analysis.defect.DefectManager
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.manager.MethodWrapperInitializer
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asm.transform.LoopDeroller
import org.vorpal.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.vorpal.research.kex.reanimator.collector.SetterCollector
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.analysis.LoopSimplifier
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.executePipeline

internal fun preparePackage(
    ctx: ExecutionContext,
    psa: PredicateStateAnalysis,
    pkg: Package
) = executePipeline(ctx.cm, pkg) {
    +MethodWrapperInitializer(ctx.cm)
    +LoopSimplifier(ctx.cm)
    +LoopDeroller(ctx.cm)
    +psa
    +MethodFieldAccessCollector(ctx, psa)
    +SetterCollector(ctx)
    +ClassInstantiationDetector(ctx)
}

@ExperimentalSerializationApi
class DefectCheckerLauncher(classPaths: List<String>, targetName: String) : KexAnalysisLauncher(classPaths, targetName) {
    override fun launch() {
        val psa = PredicateStateAnalysis(context.cm)

        preparePackage(context, psa, Package.defaultPackage)
        runPipeline(context ,analysisLevel) {
            +DefectChecker(context, psa)
        }

        DefectManager.emit()
    }

    override fun prepareClassPath(ctx: ExecutionContext): Pipeline.() -> Unit = {}
}
