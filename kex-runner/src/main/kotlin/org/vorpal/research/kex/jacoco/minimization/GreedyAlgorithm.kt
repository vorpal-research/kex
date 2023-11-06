package org.vorpal.research.kex.jacoco.minimization

import java.io.File

interface TestReduction

class Test(
    val reqs: List<Int>
) {
    var power: Int = reqs.size
}

class Requirement {
    val tests: MutableList<String> =mutableListOf<String>()
    var visited = false

    fun addTest(testName: String) {
        tests.add(testName)
    }

    fun visit(tests: List<Test>) {
        if (visited) return
        tests.forEach{ it.power-- }
        visited = true
    }
}

public class GreedyAlgorithm(
    testCoverage: TestwiseCoverageInfo,
    file: File
) :TestReduction{
    private val files = file
    private var tests: MutableMap<String, Test> = mutableMapOf()
    private var requirements: MutableMap<Int, Requirement> = mutableMapOf()

    init {
        testCoverage.req.forEach{ requirements[it] = Requirement() }
        for (test in testCoverage.tests) {
            tests[test.testName] = Test(test.satisfize)
            for (requirement in test.satisfize)
                requirements[requirement]!!.addTest(test.testName)
        }
    }

    fun minimized(): List<String> {
        val reqs_set = mutableSetOf<Int>()
        for (test in tests) {
            test.value.reqs.forEach { reqs_set.add(it) }
        }

        var satisfied_req: Int = 0
        val result = emptyList<String>().toMutableList()
        while (satisfied_req < requirements.size) {
            var max_test: String? = null
            tests.forEach { if (it.value.power > (tests[max_test]?.power ?: -1)) max_test = it.key }
            if ((tests[max_test]?.power ?: 0) == 0) break

            satisfied_req += tests[max_test]!!.power
            tests[max_test]!!.reqs.forEach { requirements[it]!!.tests.forEach{ tests[it]!!.power-- } }
            result.add(result.size, max_test!!)
        }

        val fileWriter = files.writer()

        // Запишите текст в файл
        fileWriter.write("Result: ${result}\n")
        fileWriter.write("Initial Coverage: ${String.format("%.2f", reqs_set.size.toDouble() / requirements.size.toDouble())}\n")
        fileWriter.write("Coverage: ${String.format("%.2f", satisfied_req.toDouble() / requirements.size.toDouble())}\n")
        fileWriter.write("Reduced: ${String.format("%.2f", result.size.toDouble() / tests.size.toDouble())}\n")

        // Закройте файл, чтобы сохранить изменения
        fileWriter.close()
        println("Result: ${result}")
        println("Initial Coverage: ${String.format("%.2f", reqs_set.size.toDouble() / requirements.size.toDouble())}")
        println("Coverage: ${String.format("%.2f", satisfied_req.toDouble() / requirements.size.toDouble())}")
        println("Reduced: ${String.format("%.2f", result.size.toDouble() / tests.size.toDouble())}")
        return result
    }
}