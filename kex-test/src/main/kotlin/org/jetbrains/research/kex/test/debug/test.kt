@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Icfpc2018Test {
    class ZipWriter {
        fun createZip(name: String): Unit = TODO()
    }

    class Results(val elements: MutableMap<String, Result>) : MutableMap<String, Result> by elements {
        companion object {
            fun readFromDirectory(dir: String): Results = TODO()
        }

        fun merge(other: Results): Results = TODO()
    }

    class Result(val solutions: MutableMap<String, Solution>) {
        fun getSortedSolutions(): List<Pair<String, Solution>> = TODO()
    }

    class Solution(val energy: Long, val trace: String) {
        fun solve() {}
    }

    enum class RunMode {
        ASSEMBLE, DISASSEMBLE, REASSEMBLE, SUBMIT, ALL
    }

    class Command {
        companion object {
            fun read(str: InputStream): Command = TODO()
        }
    }

    class Model(val size: Int, val numGrounded: Int = 0) {
        companion object {
            fun readMDL(inp: InputStream): Model = TODO()
        }
    }

    class State {
        val matrix = Model(0, 0)
    }

    class System(var currentState: State, val score: Int = 0)

    class Trace(val trace: List<Command>, val system: System) {
        fun solve(): Unit = TODO()
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
            } else {
                Intrinsics.assertReachable()

                val targetModel = when (mode) {
                    RunMode.ASSEMBLE -> Model.readMDL(File("models/${task}_tgt.mdl").inputStream())
                    else -> Model.readMDL(File("models/${task}_src.mdl").inputStream())
                }
                val state = State()

                var haveSolution = false
                for ((solutionName, solution) in result.getSortedSolutions()) {
                    Intrinsics.assertReachable()
                    val traceFile = File(solution.trace).inputStream()
                    val commands: MutableList<Command> = mutableListOf()
                    while (traceFile.available() != 0) {
                        Intrinsics.assertReachable()
                        commands += Command.read(traceFile)
                    }
                    val system = System(state)
                    try {
                        Trace(commands, system).solve()
                    } catch (e: Exception) {
                        continue
                    }

                    if (system.currentState.matrix != targetModel) {
                        Intrinsics.assertReachable()
                        continue
                    }

                    Files.copy(File(solution.trace).toPath(), File("submit/$task.nbt").toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                    haveSolution = true
                    Intrinsics.assertReachable()
                    break
                }
                if (!haveSolution) {
                    Intrinsics.assertReachable()
                    return
                }
                Intrinsics.assertReachable()
            }
        }

        Intrinsics.assertReachable()
        ZipWriter().createZip("submit/")
    }

}