package org.jetbrains.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.jetbrains.research.kex.asm.manager.ClassInstantiationDetector
import org.jetbrains.research.kex.asm.manager.MethodWrapperInitializer
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.SymbolicTraceCollector
import org.jetbrains.research.kex.asm.transform.SystemExitTransformer
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.jacoco.CoverageReporter
import org.jetbrains.research.kex.trace.symbolic.InstructionTraceManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.logging.log

@ExperimentalSerializationApi
@InternalSerializationApi
class ConcolicLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {
    override fun createInstrumenter(context: ExecutionContext): MethodVisitor {
        return SymbolicTraceCollector(context)
    }

    override fun preparePackage(ctx: ExecutionContext, psa: PredicateStateAnalysis, pkg: Package) = executePipeline(ctx.cm, pkg) {
        +ClassInstantiationDetector(ctx.cm, Visibility.PRIVATE)
    }

    override fun launch() {
        val traceManager = InstructionTraceManager()

        preparePackage(context, PredicateStateAnalysis(context.cm))
        runPipeline(context) {
            +MethodWrapperInitializer(context.cm)
            +SystemExitTransformer(context.cm)
            +InstructionConcolicChecker(context, traceManager)
        }
        log.info(
            CoverageReporter(containerClassLoader).execute(analysisLevel, printDetailedCoverage = true)
        )
    }
}