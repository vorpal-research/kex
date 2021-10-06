package org.jetbrains.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.research.kex.asm.analysis.defect.CallCiteChecker
import org.jetbrains.research.kex.asm.analysis.defect.DefectManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

@ExperimentalSerializationApi
class LibraryCheckLauncher(classPaths: List<String>, targetName: String) : KexLauncher(classPaths, targetName) {
    override fun launch() {
        val callCitePackage = Package.parse(
            kexConfig.getStringValue("libCheck", "target")
                ?: unreachable { log.error("You need to specify package in which to look for library usages") }
        )
        val psa = PredicateStateAnalysis(context.cm)

        preparePackage(context, psa)
        runPipeline(context) {
            +CallCiteChecker(context, callCitePackage, psa)
        }
        clearClassPath()
        log.debug("Analysis finished, emitting results info ${DefectManager.defectFile}")
        DefectManager.emit()
    }
}