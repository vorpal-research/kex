package org.jetbrains.research.kex.spider

import java.io.File
import java.util.*

fun main() {
    val loader = Thread.currentThread().contextClassLoader
    val resources = loader.getResource("org/jetbrains/research/kex/spider/")?.toURI()
        ?: error("resources dir not found")
    val testData = File(resources)
    val generatedTests = mutableListOf<String>()

    for (testDir in testData.listFiles(File::isFile).orEmpty().filter { it.extension == "lsl" }.sorted()) {
        val testName = testDir.nameWithoutExtension
        generatedTests.add(getTestRunnerBoilerplate(testName).addIntent(4))
    }

    val generatedTestCode = buildString {
        appendLine("package org.jetbrains.research.kex.spider")
        appendLine()
        appendLine("import org.junit.Test")
        appendLine()
        appendLine("// DO NOT MODIFY THIS CODE MANUALLY!")
        appendLine("// You should use ./generateTests.kt to do it")
        appendLine()
        appendLine("class SpiderTestsGenerated {")
        append(generatedTests.joinToString("\n"))
        appendLine("}")
    }

    val generatedCodeFile = File("./kex-runner/src/test/kotlin/org/jetbrains/research/kex/spider/SpiderTestsGenerated.kt")
    generatedCodeFile.writeText(generatedTestCode)
}

private fun getTestRunnerBoilerplate(testName: String): String = buildString {
    appendLine("@Test")
    val capitalizedTestName = testName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    appendLine("fun test$capitalizedTestName() {")
    appendLineWithIntent("SpiderTestRunner(\"$testName\").runTest()", 4)
    appendLine("}")
}
