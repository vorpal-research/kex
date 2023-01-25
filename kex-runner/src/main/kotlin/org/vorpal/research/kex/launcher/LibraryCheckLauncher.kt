package org.vorpal.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.defect.CallCiteChecker
import org.vorpal.research.kex.asm.analysis.defect.DefectManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

@ExperimentalSerializationApi
class LibraryCheckLauncher(
    classPaths: List<String>,
    targetName: String,
    callCitePackage: String
) : KexAnalysisLauncher(classPaths, targetName) {
    private val callCitePackage: Package = Package.parse(callCitePackage)
    override fun launch() {
        val psa = PredicateStateAnalysis(context.cm)

        preparePackage(context, psa, Package.defaultPackage)
        runPipeline(context, analysisLevel) {
            +CallCiteChecker(context, callCitePackage, psa)
        }
        log.debug("Analysis finished, emitting results info ${DefectManager.defectFile}")
        DefectManager.emit()
    }

    override fun prepareClassPath(ctx: ExecutionContext): Pipeline.() -> Unit = {}
}
