package org.vorpal.research.kex.reanimator.codegen

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.compile.JavaCompilerDriver
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.codegen.javagen.ActionSequence2JavaPrinter
import org.vorpal.research.kex.reanimator.codegen.javagen.ExecutorAS2JavaPrinter
import org.vorpal.research.kex.reanimator.codegen.kotlingen.ActionSequence2KotlinPrinter
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.getJunit
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kex.util.testcaseDirectory
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.nio.file.Paths
import kotlin.math.abs

private val useReanimator by lazy { kexConfig.getBooleanValue("reanimator", "enabled", true) }
private val generateTestCases by lazy { kexConfig.getBooleanValue("testGen", "enabled", false) }
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

    @Suppress("unused")
    fun checkTestCompiles() {
        if (testCaseLanguage == "java") {
            val compileDir = kexConfig.compiledCodeDirectory
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
    private val testDirectory = kexConfig.testcaseDirectory
    override val printer: ActionSequencePrinter = when (testCaseLanguage) {
        "kotlin" -> ActionSequence2KotlinPrinter(
            ctx,
            packageName.javaString,
            klassName
        )
        "java" -> ActionSequence2JavaPrinter(
            ctx,
            packageName.javaString,
            klassName
        )
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
    private val testDirectory = kexConfig.testcaseDirectory
    val fullKlassName = "${packageName.javaString}.$klassName"
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

    fun printWithAssertions(
            testName: String,
            method: Method,
            actionSequences: Parameters<ActionSequence>,
            previousExecutionResult: UnsafeGenerator.TestCaseResultInfo?
    ) {
        printer.printActionSequence(validateString(testName), method, actionSequences, previousExecutionResult)
    }

    fun print(method: Method, actionSequences: Parameters<ActionSequence>) {
        print(TEST_METHOD, method, actionSequences)
    }

    fun printWithAssertions(
            method: Method,
            actionSequences: Parameters<ActionSequence>,
            previousExecutionResult: UnsafeGenerator.TestCaseResultInfo?) {
        printWithAssertions(TEST_METHOD, method, actionSequences, previousExecutionResult)
    }

    override fun emit() {
        targetFile.writeText(printer.emit())
    }
}
