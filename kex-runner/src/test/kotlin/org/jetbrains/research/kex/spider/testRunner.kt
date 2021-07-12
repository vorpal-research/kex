package org.jetbrains.research.kex.spider

import org.jetbrains.research.kex.KexRunnerTest
import org.jetbrains.research.kex.asm.analysis.defect.DefectManager
import org.jetbrains.research.kex.asm.analysis.libchecker.CallCiteChecker
import org.jetbrains.research.kex.asm.analysis.libchecker.LibslInstrumentator
import org.jetbrains.research.kex.asm.manager.OriginalMapper
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.BranchAdapter
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.libsl.LibslDescriptor
import org.jetbrains.research.kex.reanimator.collector.ExternalCtorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kex.util.getIntrinsics
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path


class SpiderTestRunner : KexRunnerTest() {
    private val tempDir: File = kexConfig.getPathValue("kex", "outputDir")!!.toFile().resolve("spidertests")

    init {
        RuntimeConfig.setValue("defect", "outputFile", tempDir.absolutePath + "/defects.json")
    }

    fun testRunner(testName: String) {
        val loader = Thread.currentThread().contextClassLoader
        val lslRes = loader.getResource("org/jetbrains/research/kex/spider/$testName.lsl")?.toURI() ?: error("lsl file not found")
        val lslFile = File(lslRes)

        tempDir.deleteRecursively()
        tempDir.mkdirs()

        updateClassPath(analysisContext.loader as URLClassLoader)

        runPipeline(testName, lslFile.absolutePath)
    }

    private fun runPipeline(testName: String, lslPath: String) {
        val psa = PredicateStateAnalysis(analysisContext.cm)
        val librarySpecification = LibslDescriptor(lslPath)

        executePipeline(cm, `package`) {
            +TestVisitor(cm)
            +OriginalMapper(analysisContext.cm, originalContext.cm)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +LibslInstrumentator(cm, librarySpecification)
            +BranchAdapter(analysisContext.cm)
            +psa
            +MethodFieldAccessCollector(analysisContext, psa)
            +SetterCollector(analysisContext)
            +ExternalCtorCollector(analysisContext.cm, Visibility.PUBLIC)
        }

        executePipeline(cm, `package`) {
            +CallCiteChecker(analysisContext, `package`, psa)
            +ClassWriter(analysisContext, tempDir.toPath())
        }

        clearClassPath()
        DefectManager.emit()
    }
}

class TestVisitor(override val cm: ClassManager) : MethodVisitor {
    override fun cleanup() {

    }

    override fun visit(method: Method) {
        if (method.mn !== method.klass.cn.methods.first { it.name == method.name && it.desc == method.asmDesc }) {
            println()
        }
        super.visit(method)
    }
}