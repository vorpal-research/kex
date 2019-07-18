@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Icfpc2018Test
import org.jetbrains.research.kex.test.Intrinsics
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Line constructor(val b: Double, val angle: Double) {
    companion object {
        fun digitNumber(n: Int): Int =
                if (n in -9..9) 1
                else digitNumber(n / 10) + digitNumber(n % 10)
    }

    override fun equals(other: Any?) = other is Line && angle == other.angle && b == other.b

    override fun hashCode(): Int {
        var result = b.hashCode()
        result = 31 * result + angle.hashCode()
        return result
    }

    override fun toString() = "Line(${Math.cos(angle)} * y = ${Math.sin(angle)} * x + $b)"
}


class Icfpc2018Test {
    data class Point(val x: Int, val y: Int, val z: Int)

    class ZipWriter {
        fun createZip(name: String): Unit = TODO()
    }

    class Results(val elements: MutableMap<String, Result>) : MutableMap<String, Result> by elements {
        companion object {
            fun readFromDirectory(dir: String): Results = TODO()
        }

        fun merge(other: Results): Results = TODO()
    }

    class System(var currentState: Icfpc2018Test.State, val score: Int = 0)

    class Result {
        fun getSortedSolutions(): List<Pair<String, Solution>> = TODO()
    }

    class Solution(val trace: String)

    enum class RunMode {
        ASSEMBLE, REASSEMBLE
    }

    private fun getModeByModelName(name: String): RunMode = TODO()

    fun submitChecked(resultDirs: List<String>) {
        val results = resultDirs.map { Results.readFromDirectory(it) }
        val merged = results.reduce { acc, res -> acc.merge(res) }
        for ((task, result) in merged) {
            val mode = getModeByModelName(task)
            if (mode == RunMode.REASSEMBLE) {
                Intrinsics.assertReachable()

                val bestSolution = result.getSortedSolutions().first().second
                Files.copy(File(bestSolution.trace).toPath(), File("submit/$task.nbt").toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }

        Intrinsics.assertReachable()
        ZipWriter().createZip("submit/")
    }
}