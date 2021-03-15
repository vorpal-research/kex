package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.codegen.javagen.CallStack2JavaPrinter
import org.jetbrains.research.kex.reanimator.codegen.kotlingen.CallStack2KotlinPrinter
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import java.io.File
import kotlin.math.abs
import java.nio.file.Paths

private val useApiGeneration by lazy { kexConfig.getBooleanValue("apiGeneration", "enabled", true) }
private val generateTestCases by lazy { kexConfig.getBooleanValue("apiGeneration", "generateTestCases", false) }
private val testCaseDirectory by lazy { kexConfig.getStringValue("apiGeneration", "testCaseDirectory", "./tests") }
private val testCaseLanguage by lazy { kexConfig.getStringValue("apiGeneration", "testCaseLanguage", "java") }

class TestCasePrinter(val ctx: ExecutionContext, val method: Method) {
    private val printer: CallStackPrinter
    private var isEmpty = true
    val packageName = method.`class`.`package`.name
    val klassName = "${method.`class`.validName}_${method.validName}_${abs(method.hashCode())}"

    private val Class.validName get() = name.replace("$", "_")
    private val Method.validName get() = name.replace(Regex("[^a-zA-Z0-9]"), "")
    private val BasicBlock.validName get() = name.toString().replace(Regex("[^a-zA-Z0-9]"), "")

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

    fun emit() {
        if (useApiGeneration && generateTestCases && !isEmpty) {
            val packageDir = Paths.get(testCaseDirectory, packageName)
            val targetFileName = when (testCaseLanguage) {
                "kotlin" -> "$klassName.kt"
                "java" -> "$klassName.java"
                else -> klassName
            }
            val targetFile = File(packageDir.toFile(), targetFileName).apply {
                parentFile?.mkdirs()
            }
            targetFile.writeText(printer.emit())
        }
    }
}
