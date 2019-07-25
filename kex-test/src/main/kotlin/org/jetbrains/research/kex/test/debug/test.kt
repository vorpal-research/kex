@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import java.util.*

class GenTest <T: Any> {
    fun testT(t: T) {
        if (t is String) {
            println(t.length)
        } else if (t is Collection<*>) {
            println(t)
        } else {
            println("Any")
        }
    }

    fun <K : Collection<*>> testK(k: K) {
        if (k is List<*>) println("List ${k.size}")
        else if (k is Set<*>) println("Set ${k.size}")
        else println("Collection ${k.size}")
    }

    inline fun <reified K : Collection<String>> testKR(k: K) {
        if (k is List<*>) println(k)
        else println("fail")
    }

    fun testX(list: List<Int>) {
        val sum = list[0] + list[1]
        println(sum)
        if (list is LinkedList<Int>) {
            println("AAAAAaa")
        } else {
            println("BBBB")
        }
    }
}

//class Icfpc2018Test {
//    class Results(val elements: MutableMap<String, Result>) : MutableMap<String, Result> by elements {
//        companion object {
//            fun readFromDirectory(dir: String): Results = Results(mutableMapOf(dir to Result()))
//        }
//
//        fun merge(other: Results): Results {
//            val elements = this.elements.toMap().toMutableMap()
//            elements.putAll(other.elements)
//            return Results(elements)
//        }
//    }
//
//    class Result {
//        fun getSortedSolutions(): List<Pair<String, Solution>> = listOf(
//                "aaaa" to Solution("aaaa"),
//                "bbbb" to Solution("bbbb")
//        )
//    }
//
//    class Solution(val trace: String)
//
//    enum class RunMode {
//        ASSEMBLE, REASSEMBLE
//    }
//
//    private fun getModeByModelName(name: String): RunMode = RunMode.REASSEMBLE
//
//    fun submitChecked(resultDirs: List<String>) {
//        val results = resultDirs.map { Results.readFromDirectory(it) }
//        val merged = results.reduce { acc, res -> acc.merge(res) }
//        for ((task, result) in merged) {
//            val mode = getModeByModelName(task)
//            if (mode == RunMode.REASSEMBLE) {
//                Intrinsics.assertReachable()
//
//                val bestSolution = result.getSortedSolutions().first().second
//                Files.copy(File(bestSolution.trace).toPath(), File("submit/$task.nbt").toPath(),
//                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
//            }
//        }
//
//        Intrinsics.assertReachable()
//    }
//}