package org.vorpal.research.kex.launcher

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.InstructionSymbolicChecker
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.minimization.Minimizer
import org.vorpal.research.kex.jacoco.reportCoverage
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.Pipeline
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class SymbolicLauncher(classPaths: List<String>, targetName: String) : KexAnalysisLauncher(classPaths, targetName) {

    override fun prepareClassPath(ctx: ExecutionContext): Pipeline.() -> Unit = {}

    private val batchedTargets: Set<Set<Method>>
        get() = when (analysisLevel) {
            is ClassLevel -> setOf(analysisLevel.klass.allMethods)
            is MethodLevel -> setOf(setOf(analysisLevel.method))
            is PackageLevel -> context.cm.getByPackage(analysisLevel.pkg).mapTo(mutableSetOf()) { it.allMethods }
        }

    override fun launch() {
        for (setOfTargets in batchedTargets) {
            InstructionSymbolicChecker.run(context, setOfTargets)
        }

        if (kexConfig.getBooleanValue("kex", "minimization", false)) {
            Minimizer(containers, context.cm, analysisLevel).execute(true, "symbolic")
        } else {
            reportCoverage(containers, context.cm, analysisLevel, "symbolic")
        }
    }
}
