@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE")

package org.jetbrains.research.kex.test.debug

import org.jetbrains.research.kex.test.Intrinsics

// add functions here to debug them

//fun testSimple(array: IntArray) {
//    var count = 0
//    if (array.size < 4) throw IllegalArgumentException()
//    if (array[0] == 1) ++count
//    if (array[1] == 2) ++count
//    if (array[2] == 3) ++count
//    if (array[3] == 4) ++count
//    if (count >= 3) {
//        println("Yes")
//        Intrinsics.assertReachable()
//    }
//}


fun testUnreachableIf(x: Int): Int {
    val set = "asdasdal;djadslas;d".length
    val z = 10
    val y = if (x > z && x < 0) {
        Intrinsics.assertUnreachable()
        println("lol")
        142
    } else {
        Intrinsics.assertReachable(x <= z || x >= 0)
        println("lol2")
        x- 2 * x
    }
    Intrinsics.assertReachable()
    return y
}

//fun testObjectArray(nullable: Array<Any?>, nonnulable: Array<Any>) {
//    if (nonnulable.isNotEmpty()) {
//        for (i in nonnulable) {
//            Intrinsics.assertReachable(i != null)
//        }
//        for (i in nonnulable) {
//            if (i == null) Intrinsics.assertUnreachable()
//        }
//    }
//    Intrinsics.assertReachable(nullable != null)
//}

//fun getModeByModelName(name: String): Icfpc2018Test.RunMode = TODO()
//
//fun submitChecked(resultDirs: List<String>) {
//    val results = resultDirs.map { Icfpc2018Test.Results.readFromDirectory(it) }
//    val merged = results.reduce { acc, res -> acc.merge(res) }
//    for ((task, result) in merged) {
//        val mode = getModeByModelName(task)
//        if (mode == Icfpc2018Test.RunMode.REASSEMBLE) {
//            Intrinsics.assertReachable()
//
//            val bestSolution = result.getSortedSolutions().first().second
//            Files.copy(File(bestSolution.trace).toPath(), File("submit/$task.nbt").toPath(),
//                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
//        } else {
//            Intrinsics.assertReachable()
//
//            val targetModel = when (mode) {
//                Icfpc2018Test.RunMode.ASSEMBLE -> Icfpc2018Test.Model.readMDL(File("models/${task}_tgt.mdl").inputStream())
//                else -> Icfpc2018Test.Model.readMDL(File("models/${task}_src.mdl").inputStream())
//            }
//            val state = Icfpc2018Test.State()
//
//            var haveSolution = false
//            for ((solutionName, solution) in result.getSortedSolutions()) {
//                Intrinsics.assertReachable()
//                val traceFile = File(solution.trace).inputStream()
//                val commands: MutableList<Icfpc2018Test.Command> = mutableListOf()
//                while (traceFile.available() != 0) {
//                    Intrinsics.assertReachable()
//                    commands += Icfpc2018Test.Command.read(traceFile)
//                }
//                val system = Icfpc2018Test.System(state)
//                try {
//                    Icfpc2018Test.Trace(commands, system).solve()
//                } catch (e: Exception) {
//                    continue
//                }
//
//                if (system.currentState.matrix != targetModel) {
//                    Intrinsics.assertReachable()
//                    continue
//                }
//
//                Files.copy(File(solution.trace).toPath(), File("submit/$task.nbt").toPath(),
//                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
//                haveSolution = true
//                Intrinsics.assertReachable()
//                break
//            }
//            if (!haveSolution) {
//                Intrinsics.assertReachable()
//                return
//            }
//            Intrinsics.assertReachable()
//        }
//    }
//
//    Intrinsics.assertReachable()
//    Icfpc2018Test.ZipWriter().createZip("submit/")
//}