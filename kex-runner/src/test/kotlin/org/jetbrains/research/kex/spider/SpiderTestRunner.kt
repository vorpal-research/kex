package org.jetbrains.research.kex.spider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.KexRunnerTest
import org.jetbrains.research.kex.asm.analysis.defect.DefectManager
import org.jetbrains.research.kex.asm.analysis.libchecker.CallCiteChecker
import org.jetbrains.research.kex.asm.analysis.libchecker.ClassInstrumentator
import org.jetbrains.research.kex.asm.analysis.libchecker.LibslInstrumentator
import org.jetbrains.research.kex.asm.manager.OriginalMapper
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.BranchAdapter
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.reanimator.collector.ExternalCtorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.libsl.LibSL
import org.junit.Assert
import java.io.File
import java.net.URLClassLoader


class SpiderTestRunner(private val testName: String) : KexRunnerTest(
    initTR = true,
    initIntrinsics = true,
    testName
) {
    private val tempDir: File = kexConfig.getPathValue("kex", "outputDir")!!.toFile().resolve("spidertests")
    private val referenceDir = File("kex-runner/src/test/kotlin/org/jetbrains/research/kex/spider/reference/")

    init {
        RuntimeConfig.setValue("defect", "outputFile", tempDir.absolutePath + "/defects.json")
    }

    private val json = Json { prettyPrint = true }

    fun runTest() {
        val loader = Thread.currentThread().contextClassLoader
        val lslRes = loader.getResource("org/jetbrains/research/kex/spider/$testName.lsl")?.toURI() ?: error("lsl file not found")
        val lslFile = File(lslRes)

        tempDir.deleteRecursively()
        tempDir.mkdirs()

        updateClassPath(analysisContext.loader as URLClassLoader)

        runPipeline(lslFile.absolutePath)

        val defectsFile = File(tempDir.absolutePath + "/defects.json")
        if (!defectsFile.exists()) {
            error("no defects file was received")
        }
        val actual = DefectManager.defects.sortedBy { it.testFile }
        val actualJson = json.encodeToString(actual)

        val referenceFile = referenceDir.resolve(testName).resolve("defects.json")
        if (!referenceFile.exists()) {
            referenceFile.apply { parentFile.mkdirs() }.createNewFile()
            referenceFile.writeText(actualJson)
            return
        }
        val referenceJson = referenceFile.readText()
        Assert.assertEquals("results are different for test $testName", referenceJson, actualJson)
    }

    private fun runPipeline(lslPath: String) {
        val psa = PredicateStateAnalysis(analysisContext.cm)
        val lslFile = File(lslPath)
        val librarySpecification = LibSL(lslFile.parent)
        val library = librarySpecification.loadFromFile(lslFile)
        val context = librarySpecification.context
        val ci = ClassInstrumentator(cm, library)

        executePipeline(cm, packages) {
            +OriginalMapper(analysisContext.cm, originalContext.cm)
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +ci
            +LibslInstrumentator(cm, library, context, ci.syntheticContexts)
            +BranchAdapter(analysisContext.cm)
            +psa
            +MethodFieldAccessCollector(analysisContext, psa)
            +SetterCollector(analysisContext)
            +ExternalCtorCollector(analysisContext.cm, Visibility.PUBLIC)
        }

        executePipeline(cm, packages) {
            +CallCiteChecker(analysisContext, `package`, psa)
            +ClassWriter(analysisContext, tempDir.toPath())
        }

        clearClassPath()
        DefectManager.emit()
    }
}