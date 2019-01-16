package org.jetbrains.research.kex.test.debug

// add functions here to debug them

class Icfpc2018Test {
    class Result(val solutions: MutableMap<String, Solution>) {
        fun getSortedSolutions(): List<Pair<String, Solution>> = TODO()
    }

    class Solution(val energy: Long, val trace: String) {
        fun solve() {}
    }
}