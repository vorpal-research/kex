package org.jetbrains.research.kex.trace

import org.jetbrains.research.kex.util.error
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import java.util.*

data class BlockInfo internal constructor(val bb: BasicBlock, val predecessor: BlockInfo?) {
    var outputAction: BlockExitAction? = null
        internal set

    val hasPredecessor: Boolean
        get() = predecessor != null

    val hasOutput: Boolean
        get() = outputAction != null
}

class Trace private constructor(val method: Method,
                                val instance: ActionValue?,
                                val args: Array<ActionValue>,
                                val blocks: Map<BasicBlock, List<BlockInfo>>,
                                val retval: ActionValue?,
                                val throwable: ActionValue?,
                                val exception: Throwable?,
                                val subtraces: List<Trace>) {
    fun getBlockInfo(bb: BasicBlock) = blocks.getValue(bb)

    companion object {
        fun parse(actions: List<Action>, exception: Throwable?): Trace {
            class Info(var instance: ActionValue?,
                       var args: Array<ActionValue>, var blocks: MutableMap<BasicBlock, MutableList<BlockInfo>>,
                       var retval: ActionValue?, var throwval: ActionValue?,
                       val exception: Throwable?, val subinfos: MutableList<Pair<Method, Info>>) {
                fun toTrace(method: Method): Trace =
                        Trace(method, instance, args, blocks, retval, throwval, exception, subinfos.map { it.second.toTrace(it.first) })
            }

            val infos = ArrayDeque<Info>()
            val methodStack = Stack<Method>()
            val result = arrayListOf<Pair<Method, Info>>()
            var previousBlock: BlockInfo? = null

            for (action in actions) {
                when (action) {
                    is MethodEntry -> {
                        methodStack.push(action.method)
                        val newInfo = Info(null, arrayOf(), hashMapOf(), null, null, exception, arrayListOf())
                        infos.peek()?.subinfos?.add(action.method to newInfo)
                        infos.push(newInfo)
                    }
                    is MethodInstance -> {
                        val info = infos.peek()
                        info.instance = action.instance.rhv
                    }
                    is MethodArgs -> {
                        val info = infos.peek()
                        info.args = action.args.map { it.rhv }.toTypedArray()
                    }
                    is MethodReturn -> {
                        val info = infos.peek()
                        info.retval = action.`return`?.rhv

                        result.add(methodStack.pop() to info)
                        infos.pop()
                    }
                    is MethodThrow -> {
                        val info = infos.peek()
                        info.throwval = action.throwable.rhv

                        result.add(methodStack.pop() to info)
                        infos.pop()
                    }
                    is BlockEntryAction -> {
                        val bb = action.bb
                        val info = infos.peek()

                        val bInfo = BlockInfo(bb, previousBlock)
                        previousBlock = bInfo
                        info.blocks.getOrPut(bb, ::arrayListOf).add(bInfo)
                    }
                    is BlockExitAction -> {
                        requireNotNull(previousBlock) {
                            log.error("Incorrect action format: Block exit without entering")
                            log.error(methodStack.peek())
                            log.error(actions.joinToString(separator = "\n"))
                        }
                        previousBlock?.outputAction = action
                    }
                }
            }
            require(methodStack.isEmpty() && infos.isEmpty())
            return result.map { it.second.toTrace(it.first) }.firstOrNull()
                    ?: unreachable {
                        log.error("Could not parse trace:")
                        log.error(actions.joinToString("\n"))
                    }
        }
    }
}

object TraceManager {
    private val methods = hashMapOf<Method, MutableList<Trace>>()

    fun getTraces(method: Method): List<Trace> = methods.getOrPut(method, ::arrayListOf)
    fun getBlockInfos(bb: BasicBlock): List<BlockInfo> = when {
        bb.parent != null -> getTraces(bb.parent!!).map { it.blocks[bb] ?: listOf() }.flatten()
        else -> listOf()
    }

    fun addTrace(method: Method, info: Trace) = methods.getOrPut(method, ::arrayListOf).add(info)

    fun isCovered(bb: BasicBlock) = getBlockInfos(bb)
            .fold(false) { acc, it -> acc || it.hasOutput }

    fun isCovered(method: Method, bb: BasicBlock) = getTraces(method).mapNotNull { it.blocks[bb] }.flatten()
            .fold(false) { acc, it -> acc || it.hasOutput }

    fun isPartlyCovered(method: Method): Boolean = method.basicBlocks.fold(false) { acc, bb -> acc or isCovered(bb) }
    fun isBodyCovered(method: Method) = method.bodyBlocks.asSequence().map { isCovered(it) }.fold(true) { acc, it -> acc && it }

    fun isCatchCovered(method: Method) = method.catchBlocks.asSequence().map { isCovered(it) }.fold(true) { acc, it -> acc && it }
    fun isFullCovered(method: Method) = isBodyCovered(method) && isCatchCovered(method)
}