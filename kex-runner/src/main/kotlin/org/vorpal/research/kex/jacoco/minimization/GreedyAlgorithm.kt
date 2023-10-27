package org.vorpal.research.kex.jacoco.minimization

interface TestReduction

class Test {
    var power = 0
    val reqs: MutableList<Int> = mutableListOf<Int>()

    fun addRequirements(req: List<Int>) {
        for (elem in req)
            reqs.add(elem)
        power += req.size
    }
}

class Requirement {
    val tests: MutableList<Int> =mutableListOf<Int>()
    var visited = false

    fun addTest(test: Int) {
        tests.add(test)
    }

    fun visit(tests: List<Test>) {
        if (visited) return
        tests.forEach{ it.power-- }
        visited = true
    }
}

public class GreedyAlgorithm(
    _req: Int,
    _test: Int,
    _dependecies: Map<Int, List<Int>>
) :TestReduction{
    private var tests = List(_test) { Test() }
    private var requirements = List(_req) { Requirement() }

    init {
        for (test_num in _dependecies) {
            tests[test_num.key].addRequirements(test_num.value)
            for (requirement in test_num.value)
                requirements[requirement].addTest(test_num.key)
        }
    }

    fun minimized(): List<Test> {
        val satisfied_req: Int = 0
        val result = emptyList<Test>().toMutableList()
        while (satisfied_req < requirements.size) {
            var max_test = tests[0]
            tests.forEach { if (it.power > max_test.power) max_test = it }
            if (max_test.power == 0) break
            max_test.reqs.forEach { requirements[it].visit(tests) }
            result.add(result.size, max_test)
        }
        return result
    }
}