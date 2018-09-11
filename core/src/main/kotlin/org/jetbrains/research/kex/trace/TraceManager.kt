package org.jetbrains.research.kex.trace

import org.jetbrains.research.kex.util.log
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

class Trace internal constructor(val method: Method, val instance: ActionValue?,
                                 val args: Array<ActionValue>, val blocks: Map<BasicBlock, List<BlockInfo>>,
                                 val retval: ActionValue?, val throwval: ActionValue?,
                                 val exception: Throwable?) {
    fun getBlockInfo(bb: BasicBlock) = blocks.getValue(bb)

    companion object {
        fun parse(actions: List<Action>, exception: Throwable?): List<Trace> {
            class Info(var instance: ActionValue?,
                       var args: Array<ActionValue>, var blocks: MutableMap<BasicBlock, MutableList<BlockInfo>>,
                       var retval: ActionValue?, var throwval: ActionValue?,
                       val exception: Throwable?)

            val infos = Stack<Info>()
            val methodStack = Stack<Method>()
            val result = arrayListOf<Trace>()
            var previousBlock: BlockInfo? = null

            for (action in actions) {
                when (action) {
                    is MethodEntry -> {
                        methodStack.push(action.method)
                        infos.push(Info(null, arrayOf(), hashMapOf(), null, null, exception))
                    }
                    is MethodInstance -> {
                        require(methodStack.peek() == action.method) { log.error("Incorrect action format: Instance action for wrong method") }
                        val info = infos.peek()
                        info.instance = action.instance.rhv
                    }
                    is MethodArgs -> {
                        require(methodStack.peek() == action.method) { log.error("Incorrect action format: Arg action for wrong method") }
                        val info = infos.peek()
                        info.args = action.args.map { it.rhv }.toTypedArray()
                    }
                    is MethodReturn -> {
                        require(methodStack.peek() == action.method) { log.error("Incorrect action format: Return action for wrong method") }
                        val info = infos.peek()
                        info.retval = action.`return`?.rhv

                        result.add(Trace(methodStack.pop(), info.instance, info.args, info.blocks, info.retval, info.throwval, info.exception))
                        infos.pop()
                    }
                    is MethodThrow -> {
                        require(methodStack.peek() == action.method) { log.error("Incorrect action format: Throw action for wrong method") }
                        val info = infos.peek()
                        info.throwval = action.throwable.rhv

                        result.add(Trace(methodStack.pop(), info.instance, info.args, info.blocks, info.retval, info.throwval, info.exception))
                        infos.pop()
                    }
                    is BlockEntryAction -> {
                        val bb = action.bb
                        require(methodStack.peek() == bb.parent) { log.error("Incorrect action format: Block action for wrong method") }
                        val info = infos.peek()

                        val bInfo = BlockInfo(bb, previousBlock)
                        previousBlock = bInfo
                        info.blocks.getOrPut(bb, ::arrayListOf).add(bInfo)
                    }
                    is BlockExitAction -> {
                        val bb = action.bb
                        require(methodStack.peek() == bb.parent) { log.error("Incorrect action format: Block action for wrong method") }
                        requireNotNull(previousBlock) { log.error("Incorrect action format: Block exit without entering") }
                        previousBlock?.outputAction = action
                    }
                }
            }
            while (methodStack.isNotEmpty()) {
                val info = infos.peek()
                result.add(Trace(methodStack.pop(), info.instance, info.args, info.blocks, info.retval, info.throwval, info.exception))
                infos.pop()
            }
            return result
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

    fun isCovered(bb: BasicBlock) = getBlockInfos(bb).fold(false) { acc, it -> acc or it.hasOutput }
    fun isPartlyCovered(method: Method): Boolean = method.basicBlocks.fold(false) { acc, bb -> acc or isCovered(bb) }
    fun isBodyCovered(method: Method) = method.bodyBlocks.map { isCovered(it) }.fold(true) { acc, it -> acc && it }

    fun isCatchCovered(method: Method) = method.catchBlocks.map { isCovered(it) }.fold(true) { acc, it -> acc && it }
    fun isFullCovered(method: Method) = isBodyCovered(method) && isCatchCovered(method)
}