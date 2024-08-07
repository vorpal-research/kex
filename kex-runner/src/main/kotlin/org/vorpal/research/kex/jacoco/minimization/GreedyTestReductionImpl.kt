package org.vorpal.research.kex.jacoco.minimization

import com.jetbrains.rd.util.first
import org.vorpal.research.kex.config.kexConfig
import java.nio.file.Path

private class Test(
    val reqs: Set<Pair<String, Int>>
) {
    var power: Int = reqs.size
}

private class Requirement {
    private val satisfyTests = mutableListOf<Path>()
    private var visited = false

    fun addTest(testName: Path) {
        satisfyTests.add(testName)
    }

    fun visit(tests: MutableMap<Path, Test>) {
        if (visited) return
        satisfyTests.forEach { tests[it]!!.power-- }
        visited = true
    }
}

class GreedyTestReductionImpl : TestSuiteMinimizer {
    private val tests = mutableMapOf<Path, Test>()
    private val requirements = mutableMapOf<Pair<String, Int>, Requirement>()

    override fun minimize(testCoverage: TestwiseCoverageInfo, deleteMinimized: Boolean): Set<Path> {
        testCoverage.req.forEach { requirements[it] = Requirement() }
        for (test in testCoverage.tests) {
            tests[test.testName] = Test(test.satisfies)
            for (requirement in test.satisfies)
                requirements[requirement]!!.addTest(test.testName)
        }

        val requestSet = tests.values.flatMapTo(mutableSetOf()) { it.reqs }

        var satisfiedReq = 0
        val importantTests = mutableSetOf<Path>()
        val maxTests = kexConfig.getIntValue("testGen", "maxTests", Integer.MAX_VALUE)
        while (satisfiedReq < requestSet.size && importantTests.size < maxTests) {
            val (maxTestPath, maxTest) = tests.maxByOrNull { it.value.power } ?: break
            if (maxTest.power == 0) break

            satisfiedReq += maxTest.power
            maxTest.reqs.forEach { requirements[it]!!.visit(tests) }
            importantTests.add(maxTestPath)
        }
        // TODO don't use fragile string comparisons
        importantTests.addAll(listOf("EqualityUtils.class", "ReflectionUtils.class").map {it.getPath()})

        val allTests = tests.keys.toList()
        val reducedTests = allTests.subtract(importantTests.toSet()).toSet()
        if (deleteMinimized) {
            TestSuiteMinimizer.deleteTestCases(reducedTests)
        }

        return importantTests
    }
   private fun String.getPath(): Path {
        return tests.map { it.key }.first { it.toString().contains(this)}
   }
}
