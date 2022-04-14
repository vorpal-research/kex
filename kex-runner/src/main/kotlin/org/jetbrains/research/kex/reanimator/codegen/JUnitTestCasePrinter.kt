package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.compile.JavaCompilerDriver
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.codegen.javagen.ActionSequence2JavaPrinter
import org.jetbrains.research.kex.reanimator.codegen.javagen.ExecutorAS2JavaPrinter
import org.jetbrains.research.kex.reanimator.codegen.kotlingen.ActionSequence2KotlinPrinter
import org.jetbrains.research.kex.util.getJunit
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import java.io.File
import java.nio.file.Paths
import kotlin.math.abs

private val useReanimator by lazy { kexConfig.getBooleanValue("reanimator", "enabled", true) }
private val generateTestCases by lazy { kexConfig.getBooleanValue("testGen", "enabled", false) }
private val outputDirectory by lazy { kexConfig.getPathValue("kex", "outputDir")!! }
private val testCaseDirectory by lazy { kexConfig.getPathValue("testGen", "testsDir", "tests") }
private val testCaseLanguage by lazy { kexConfig.getStringValue("testGen", "testCaseLanguage", "java") }

val Class.validName get() = name.replace("$", "_")
val Method.validName get() = name.replace(Regex("[^a-zA-Z0-9]"), "")
val BasicBlock.validName get() = name.toString().replace(Regex("[^a-zA-Z0-9]"), "")


val Method.packageName get() = klass.pkg.concreteName
val Method.klassName get() = "${klass.validName}_${validName}_${abs(hashCode())}"

abstract class TestCasePrinter(
    val ctx: ExecutionContext,
    val packageName: String,
) {
    abstract val targetFile: File
    abstract val printer: ActionSequencePrinter

    protected fun validateString(string: String) = string.replace(Regex("[^a-zA-Z0-9]"), "")

    abstract fun print(testName: String, method: Method, actionSequences: Parameters<ActionSequence>)

    open fun emit() {
        if (useReanimator && generateTestCases) {
            targetFile.writeText(printer.emit())
        }
    }

    fun checkTestCompiles() {
        if (testCaseLanguage == "java") {
            val compileDir = outputDirectory.resolve(
                kexConfig.getPathValue("compile", "compileDir", "compiled/")
            ).also {
                it.toFile().mkdirs()
            }
            val junitPath = getJunit()?.path ?: Paths.get(".")
            val compiler = JavaCompilerDriver(ctx.classPath + listOf(junitPath), compileDir)
            tryOrNull {
                compiler.compile(listOf(targetFile.toPath()))
            }
        }
    }
}

class JUnitTestCasePrinter(
    ctx: ExecutionContext,
    packageName: String,
    klassName: String
) : TestCasePrinter(ctx, packageName) {
    private val testDirectory = outputDirectory.resolve(testCaseDirectory)
    override val printer: ActionSequencePrinter = when (testCaseLanguage) {
        "kotlin" -> ActionSequence2KotlinPrinter(ctx, packageName.replace("/", "."), klassName)
        "java" -> ActionSequence2JavaPrinter(ctx, packageName.replace("/", "."), klassName)
        else -> unreachable { log.error("Unknown target language for test case generation: $testCaseLanguage") }
    }
    override val targetFile: File = run {
        val targetFileName = when (testCaseLanguage) {
            "kotlin" -> "$klassName.kt"
            "java" -> "$klassName.java"
            else -> klassName
        }
        testDirectory.resolve(packageName).resolve(targetFileName).toAbsolutePath().toFile().apply {
            parentFile?.mkdirs()
        }
    }

    private var isEmpty = true

    override fun print(testName: String, method: Method, actionSequences: Parameters<ActionSequence>) {
        isEmpty = false
        printer.printActionSequence(validateString(testName), method, actionSequences)
    }
}

class ExecutorTestCasePrinter(
    ctx: ExecutionContext,
    packageName: String,
    val klassName: String
) : TestCasePrinter(ctx, packageName) {
    private val testDirectory = outputDirectory.resolve(testCaseDirectory)
    val fullKlassName = "${packageName.replace("/", ".")}.$klassName"
    override val printer = ExecutorAS2JavaPrinter(ctx, packageName.replace("/", "."), klassName, SETUP_METHOD)
    override val targetFile: File = run {
        val targetFileName = "$klassName.java"
        testDirectory.resolve(packageName).resolve(targetFileName).toAbsolutePath().toFile().apply {
            parentFile?.mkdirs()
        }
    }

    companion object {
        const val SETUP_METHOD = "setup"
        const val TEST_METHOD = "test"
    }

    override fun print(testName: String, method: Method, actionSequences: Parameters<ActionSequence>) {
        printer.printActionSequence(validateString(testName), method, actionSequences)
    }

    fun print(method: Method, actionSequences: Parameters<ActionSequence>) {
        print(TEST_METHOD, method, actionSequences)
    }

    override fun emit() {
        targetFile.writeText(printer.emit())
    }
}