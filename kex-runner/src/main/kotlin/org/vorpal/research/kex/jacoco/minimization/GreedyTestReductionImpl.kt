package org.vorpal.research.kex.jacoco.minimization

import java.nio.file.Path

interface TestReduction

private class Test(
    val reqs: List<Int>
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

class GreedyTestReductionImpl(
    testCoverage: TestwiseCoverageInfo
) : TestReduction {
    private var tests = mutableMapOf<Path, Test>()
    private var requirements = mutableMapOf<Int, Requirement>()

    init {
        testCoverage.req.forEach { requirements[it] = Requirement() }
        for (test in testCoverage.tests) {
            tests[test.testName] = Test(test.satisfies)
            for (requirement in test.satisfies)
                requirements[requirement]!!.addTest(test.testName)
        }
    }

    fun minimized(): List<Path> {
        val requestSet = mutableSetOf<Int>()
        for (test in tests) {
            test.value.reqs.forEach { requestSet.add(it) }
        }

        var satisfiedReq = 0
        val importantTests = emptyList<Path>().toMutableList()
        while (satisfiedReq < requestSet.size) {
            var maxTest: Path? = null
            tests.forEach { if (it.value.power > (tests[maxTest]?.power ?: -1)) maxTest = it.key }
            if ((tests[maxTest]?.power ?: 0) == 0) break

            satisfiedReq += tests[maxTest]!!.power
            tests[maxTest]!!.reqs.forEach { requirements[it]!!.visit(tests) }
            importantTests.add(importantTests.size, maxTest!!)
        }

        val allTests = tests.keys.toList()
        return allTests.subtract(importantTests.toSet()).toList()
    }
}
