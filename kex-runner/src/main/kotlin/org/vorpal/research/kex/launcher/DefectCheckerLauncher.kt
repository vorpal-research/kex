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

@ExperimentalSerializationApi
class DefectCheckerLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {
    override fun preparePackage(
        ctx: ExecutionContext,
        psa: PredicateStateAnalysis,
        pkg: Package
    ) = runPipeline(ctx, pkg) {
        +MethodWrapperInitializer(ctx.cm)
        +LoopSimplifier(ctx.cm)
        +LoopDeroller(ctx.cm)
        +psa
        +MethodFieldAccessCollector(ctx, psa)
        +SetterCollector(ctx)
        +ClassInstantiationDetector(ctx.cm, visibilityLevel)
    }

    override fun launch() {
        val psa = PredicateStateAnalysis(context.cm)

        preparePackage(context, psa)
        runPipeline(context) {
            +DefectChecker(context, psa)
        }

        DefectManager.emit()
    }
}