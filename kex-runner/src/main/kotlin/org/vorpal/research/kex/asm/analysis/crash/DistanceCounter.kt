package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kfg.ir.*
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kthelper.collection.MapWithDefault
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.collection.withDefault
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.Viewable


class MethodDistanceCounter(
    private val stackTrace: StackTrace
) {
    private val scores = mutableMapOf<Method, MapWithDefault<BasicBlock, ULong>>()

    companion object {
        private const val INF = 1_000_000UL
        private const val DEFAULT_WEIGHT = 10UL
        private const val CATCH_WEIGHT = 1000UL
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


    private fun computeMethodScores(method: Method): MapWithDefault<BasicBlock, ULong> {
        val targetInstructions = method.targetInstructions().ifEmpty {
            method.body.flatten()
                .filterIsInstanceTo<ReturnInst, MutableSet<ReturnInst>>(mutableSetOf())
        }.mapTo(mutableSetOf()) { it.parent }

        val weights = targetInstructions.associateWith { 0UL }.toMutableMap().withDefault(INF)
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

    fun score(basicBlock: BasicBlock): ULong = scores.getOrPut(basicBlock.method) {
        computeMethodScores(basicBlock.method).also {
//            viewMethod(basicBlock.method, it)
        }
    }[basicBlock]

    private fun viewMethod(method: Method, scores: MapWithDefault<BasicBlock, ULong>) {
        val viewable = object : Viewable {
            override val graphView: List<GraphView>
                get() {
                    val nodes = hashMapOf<String, GraphView>()
                    nodes[method.name] = GraphView(method.name, method.prototype)

                    for (bb in method.body.basicBlocks) {
                        val label = StringBuilder()
                        label.append("${scores[bb]}\t${bb.name}: ${bb.predecessors.joinToString(", ") { it.name.toString() }}\\l")
                        bb.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
                        nodes[bb.name.toString()] = GraphView(bb.name.toString(), label.toString())
                    }

                    if (!method.isAbstract) {
                        val entryNode = nodes.getValue(method.body.entry.name.toString())
                        nodes.getValue(method.name).addSuccessor(entryNode)
                    }

                    for (it in method.body.basicBlocks) {
                        val current = nodes.getValue(it.name.toString())
                        for (successor in it.successors) {
                            current.addSuccessor(nodes.getValue(successor.name.toString()))
                        }
                    }

                    return nodes.values.toList()
                }
        }
        viewable.view("", "/usr/bin/dot", "/usr/bin/firefox")
    }
}
