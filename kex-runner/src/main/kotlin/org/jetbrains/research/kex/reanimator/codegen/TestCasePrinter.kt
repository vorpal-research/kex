package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.codegen.javagen.CallStack2JavaPrinter
import org.jetbrains.research.kex.reanimator.codegen.kotlingen.CallStack2KotlinPrinter
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import java.io.File
import java.nio.file.Paths
import kotlin.math.abs

private val useApiGeneration by lazy { kexConfig.getBooleanValue("apiGeneration", "enabled", true) }
private val generateTestCases by lazy { kexConfig.getBooleanValue("apiGeneration", "generateTestCases", false) }
private val testCaseDirectory by lazy { kexConfig.getStringValue("apiGeneration", "testCaseDirectory", "./tests") }
private val testCaseLanguage by lazy { kexConfig.getStringValue("apiGeneration", "testCaseLanguage", "java") }

val Class.validName get() = name.replace("$", "_")
val Method.validName get() = name.replace(Regex("[^a-zA-Z0-9]"), "")
val BasicBlock.validName get() = name.toString().replace(Regex("[^a-zA-Z0-9]"), "")


val Method.packageName get() = `class`.`package`.name
val Method.klassName get() = "${`class`.validName}_${validName}_${abs(hashCode())}"

class TestCasePrinter(val ctx: ExecutionContext, val packageName: String, val klassName: String) {
    private val printer: CallStackPrinter
    private var isEmpty = true

    private val String.validName get() = this.replace(Regex("[^a-zA-Z0-9]"), "")

    val targetFile: File = run {
        val targetFileName = when (testCaseLanguage) {
            "kotlin" -> "$klassName.kt"
            "java" -> "$klassName.java"
            else -> klassName
        }
        Paths.get(testCaseDirectory, packageName, targetFileName).toAbsolutePath().toFile().apply {
            parentFile?.mkdirs()
        }
    }

    init {
        printer = when (testCaseLanguage) {
            "kotlin" -> CallStack2KotlinPrinter(ctx, packageName.replace("/", "."), klassName)
            "java" -> CallStack2JavaPrinter(ctx, packageName.replace("/", "."), klassName)
            else -> unreachable { log.error("Unknown target language for test case generation: $testCaseLanguage") }
        }
    }

    fun print(cs: CallStack, block: BasicBlock) {
        isEmpty = false
        printer.printCallStack(cs, "test_${block.validName}")
    }

    fun print(cs: CallStack, testName: String) {
        isEmpty = false
        printer.printCallStack(cs, testName.validName)
    }

    fun emit() {
        if (useApiGeneration && generateTestCases && !isEmpty) {
            val targetFileName = when (testCaseLanguage) {
                "kotlin" -> "$klassName.kt"
                "java" -> "$klassName.java"
                else -> klassName
            }
            val targetFile = Paths.get(testCaseDirectory, packageName, targetFileName).toAbsolutePath().toFile().apply {
                parentFile?.mkdirs()
            }
            targetFile.writeText(printer.emit())
        }
    }
}
