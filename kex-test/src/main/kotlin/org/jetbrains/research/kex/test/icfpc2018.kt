@file:Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNREACHABLE_CODE")

package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.Intrinsics.kexAssert
import org.jetbrains.research.kex.Intrinsics.kexUnreachable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Icfpc2018Test {
    class ZipWriter {
        fun createZip(name: String): Unit {}
    }

    class Results(val elements: MutableMap<String, Result>) : MutableMap<String, Result> by elements {
        companion object {
            fun readFromDirectory(dir: String): Results = Results(mutableMapOf(dir to Result(mutableMapOf())))
        }

        fun merge(other: Results): Results {
            val elements = this.elements.toMap().toMutableMap()
            elements.putAll(other.elements)
            return Results(elements)
        }
    }

    class Result(val solutions: MutableMap<String, Solution>) {
        fun getSortedSolutions(): List<Pair<String, Solution>> = solutions.toList().sortedBy { it.first }
    }

    class Solution(val energy: Long, val trace: String) {
        fun solve() {}
    }


    enum class RunMode {
        ASSEMBLE, DISASSEMBLE, REASSEMBLE, SUBMIT, ALL
    }

    class Command {
        companion object {
            fun read(str: InputStream): Command = Command()
        }
    }

    class Model(val size: Int, val numGrounded: Int = 0) {
        companion object {
            fun readMDL(inp: InputStream): Model = Model(10, 15)
        }
    }

    class State {
        val matrix = Model(0, 0)
    }

    class System(var currentState: State, val score: Int = 0)

    class Trace(val trace: List<Command>, val system: System) {
        fun solve() {}
    }

    private fun getModeByModelName(name: String): RunMode = RunMode.values()[name.length % 5]
    private fun getSolutionByName(name: String, target: Model, system: System): Solution = Solution(100, name)

    // todo: check some reachability conditions
    fun submitChecked(resultDirs: List<String>) {
        val results = resultDirs.map { Results.readFromDirectory(it) }
        val merged = results.reduce { acc, res -> acc.merge(res) }
        for ((task, result) in merged) {
            val mode = getModeByModelName(task)
            if (mode == RunMode.REASSEMBLE) {
                kexAssert()

                val bestSolution = result.getSortedSolutions().first().second
                Files.copy(File(bestSolution.trace).toPath(), File("submit/$task.nbt").toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            } else {
                kexAssert()

                val targetModel = when (mode) {
                    RunMode.ASSEMBLE -> Model.readMDL(ByteArrayInputStream("models/${task}_tgt.mdl".toByteArray()))
                    else -> Model.readMDL(ByteArrayInputStream("models/${task}_src.mdl".toByteArray()))
                }
                val state = State()

                var haveSolution = false
                for ((solutionName, solution) in result.getSortedSolutions()) {
                    kexAssert()
                    val traceFile = File(solution.trace).inputStream()
                    val commands: MutableList<Command> = mutableListOf()
                    while (traceFile.available() != 0) {
                        kexAssert()
                        commands += Command.read(traceFile)
                    }
                    val system = System(state)
                    try {
                        Trace(commands, system).solve()
                    } catch (e: Exception) {
                        continue
                    }

                    if (system.currentState.matrix != targetModel) {
                        kexAssert()
                        continue
                    }

                    Files.copy(File(solution.trace).toPath(), File("submit/$task.nbt").toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                    haveSolution = true
                    break
                    kexUnreachable()
                }
                if (!haveSolution) {
                    kexAssert()
                    return
                }
            }
        }

        kexAssert()
        ZipWriter().createZip("submit/")
    }

    fun portfolioSolve() {
        val task = "taskname"
        val target = Model.readMDL(ByteArrayInputStream("models/${task}_tgt.mdl".toByteArray()))
        val initialState = State()
        val system = System(initialState)
        val solutionNames = listOf("grounded_slices", "grounded_bounded_slices", "regions")

        var initialized = false
        var bestSolutionName = "none"
        for (solutionName in solutionNames) {
            var solutionSystem = System(initialState)
            val solution = getSolutionByName(solutionName, target, solutionSystem)
            try {
                solution.solve()
            } catch (e: Exception) {

                if (solutionName == "regions") {
                    try {
                        solutionSystem = System(initialState)
                        val newSolution = getSolutionByName("regions_deadlocks", target, solutionSystem)
                        newSolution.solve()
                    } catch (e: Exception) {
                        continue
                    }
                } else continue
            }

            if (solutionSystem.currentState.matrix == target) {
                if (!initialized || system.score > solutionSystem.score) {
                    system.currentState = solutionSystem.currentState
                    initialized = true
                }
            }
        }
    }

}