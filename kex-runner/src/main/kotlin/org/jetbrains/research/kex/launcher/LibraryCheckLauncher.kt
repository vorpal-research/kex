package org.jetbrains.research.kex.launcher

import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.research.kex.asm.analysis.defect.DefectManager
import org.jetbrains.research.kex.asm.analysis.libchecker.CallCiteChecker
import org.jetbrains.research.kex.asm.analysis.libchecker.ClassInstrumentator
import org.jetbrains.research.kex.asm.analysis.libchecker.LibslInstrumentator
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.BranchAdapter
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.reanimator.collector.ExternalCtorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.libsl.LibSL
import java.io.File

@ExperimentalSerializationApi
class LibraryCheckLauncher(
    classPaths: List<String>,
    targetName: String,
    private val lslFile: String
) : KexLauncher(classPaths, targetName) {
    override fun launch() {
        val callCitePackage = Package.parse(
            kexConfig.getStringValue("libCheck", "target")
                ?: unreachable { log.error("You need to specify package in which to look for library usages") }
        )
        val psa = PredicateStateAnalysis(context.cm)
        preparePackage(context, psa)

        val cm = context.cm
        val lslFile = File(lslFile)
        val librarySpecification = LibSL(lslFile.parent)
        val library = librarySpecification.loadFromFile(lslFile)
        val lslContext = librarySpecification.context
        val ci = ClassInstrumentator(cm, library)
        val tmpDir = kexConfig.getPathValue("kex", "outputDir") ?: error("missing kex:outputDir value")

        executePipeline(cm, callCitePackage) {
            +LoopSimplifier(cm)
            +LoopDeroller(cm)
            +ci
            +LibslInstrumentator(cm, library, lslContext, ci.syntheticContexts)
            +BranchAdapter(cm)
            +psa
            +MethodFieldAccessCollector(context, psa)
            +SetterCollector(context)
            +ExternalCtorCollector(cm, Visibility.PUBLIC)
        }

        executePipeline(cm, callCitePackage) {
            +CallCiteChecker(context, callCitePackage, callCitePackage, psa) // separated package should be here
            +ClassWriter(context, tmpDir)
        }
        clearClassPath()
        log.debug("Analysis finished, emitting results info ${DefectManager.defectFile}")
        DefectManager.emit()
    }
}