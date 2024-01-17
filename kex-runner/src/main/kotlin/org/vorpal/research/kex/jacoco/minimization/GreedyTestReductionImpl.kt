package org.vorpal.research.kex.jacoco.minimization

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
        while (satisfiedReq < requestSet.size) {
            val (maxTestPath, maxTest) = tests.maxByOrNull { it.value.power } ?: break
            if (maxTest.power == 0) break

            satisfiedReq += maxTest.power
            maxTest.reqs.forEach { requirements[it]!!.visit(tests) }
            importantTests.add(maxTestPath)
        }

        val allTests = tests.keys.toList()
        val reducedTests = allTests.subtract(importantTests.toSet()).toSet()
        if (deleteMinimized) {
            TestSuiteMinimizer.deleteTestCases(reducedTests)
        }

        return importantTests
    }
}
