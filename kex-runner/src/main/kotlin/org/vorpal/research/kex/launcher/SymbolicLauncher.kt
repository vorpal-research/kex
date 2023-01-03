package org.vorpal.research.kex.launcher

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicTraverser
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.CoverageReporter
import org.vorpal.research.kex.util.PermanentCoverageInfo
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log

@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class SymbolicLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {

    override fun createInstrumenter(context: ExecutionContext): MethodVisitor {
        return object : MethodVisitor {
            override val cm: ClassManager
                get() = context.cm

            override fun cleanup() {}
        }
    }

    override fun preparePackage(ctx: ExecutionContext, psa: PredicateStateAnalysis, pkg: Package) =
        executePipeline(ctx.cm, pkg) {
            +ClassInstantiationDetector(ctx)
        }

    private val batchedTargets: Set<Set<Method>>
        get() = when (analysisLevel) {
            is ClassLevel -> setOf(analysisLevel.klass.allMethods)
            is MethodLevel -> setOf(setOf(analysisLevel.method))
            is PackageLevel -> context.cm.getByPackage(analysisLevel.pkg).mapTo(mutableSetOf()) { it.allMethods }
        }

    override fun launch() {
        for (setOfTargets in batchedTargets) {
            SymbolicTraverser.run(context, setOfTargets)
        }

        val coverageInfo = CoverageReporter(containers).execute(context.cm, analysisLevel)
        log.info(
            coverageInfo.print(kexConfig.getBooleanValue("kex", "printDetailedCoverage", false))
        )

        PermanentCoverageInfo.putNewInfo("symbolic", analysisLevel.toString(), coverageInfo)
        PermanentCoverageInfo.emit()
    }
}
