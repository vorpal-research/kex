package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kfg.ir.*
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kthelper.collection.MapWithDefault
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.collection.withDefault


class MethodDistanceCounter(
    private val stackTrace: StackTrace
) {
    private val scores = mutableMapOf<Method, MapWithDefault<BasicBlock, Int>>()

    companion object {
        private const val INF = 1_000_000_000
        private const val DEFAULT_WEIGHT = 1
        private const val CATCH_WEIGHT = 1000
    }

    private infix fun Pair<Method, Location>.eq(stackTraceElement: StackTraceElement): Boolean {
        val (method, location) = this
        return method.klass.fullName.javaString == stackTraceElement.className
                && method.name == stackTraceElement.methodName
                && location.file == stackTraceElement.fileName
                && location.line == stackTraceElement.lineNumber
    }

    private fun Method.targetInstructions(): Set<Instruction> {
        return this.body.flatten().filterTo(mutableSetOf()) { inst ->
            stackTrace.stackTraceLines.any { (this to inst.location) eq it }
        }
    }


    private fun computeMethodScores(method: Method): MapWithDefault<BasicBlock, Int> {
        val targetInstructions = method.targetInstructions().ifEmpty {
            method.body.flatten()
                .filterIsInstanceTo<ReturnInst, MutableSet<ReturnInst>>(mutableSetOf())
        }.mapTo(mutableSetOf()) { it.parent }

        val weights = targetInstructions.associateWith { 0 }.toMutableMap().withDefault(INF)
        val queue = queueOf(targetInstructions)
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            for (predecessor in current.predecessors) {
                if (weights[predecessor] > (weights[current] + DEFAULT_WEIGHT)) {
                    weights[predecessor] = (weights[current] + DEFAULT_WEIGHT)
                    queue += predecessor
                }
            }
            if (current is CatchBlock) {
                for (thrower in current.throwers) {
                    if (weights[thrower] > (weights[current] + CATCH_WEIGHT)) {
                        weights[thrower] = (weights[current] + CATCH_WEIGHT)
                        queue += thrower
                    }
                }
            }
        }

        return weights.toMap().withDefault(INF)
    }

    fun score(basicBlock: BasicBlock): Int = scores.getOrPut(basicBlock.method) {
        computeMethodScores(basicBlock.method)
    }[basicBlock]
}
