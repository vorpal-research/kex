package org.jetbrains.research.kex.runner

import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import java.util.*

data class BlockInfo internal constructor(val bb: BasicBlock, val predecessor: BlockInfo?) {
    var outputAction: BlockExitAction? = null
        internal set

    fun hasPredecessor() = predecessor != null
    fun hasOutput() = outputAction != null
}

class MethodInfo internal constructor(val method: Method, val instance: ActionValue?,
                                      val args: Array<ActionValue>, val blocks: Map<BasicBlock, List<BlockInfo>>,
                                      val retval: ActionValue?, val throwval: ActionValue?,
                                      val exception: Throwable?) {
    fun getBlockInfo(bb: BasicBlock) = blocks.getValue(bb)

    companion object {
        fun parse(actions: List<Action>, exception: Throwable?): List<MethodInfo> {
            val log = loggerFor(this::class.java)

            class Info(var instance: ActionValue?,
                       var args: Array<ActionValue>, var blocks: MutableMap<BasicBlock, MutableList<BlockInfo>>,
                       var retval: ActionValue?, var throwval: ActionValue?,
                       val exception: Throwable?)

            val infos = Stack<Info>()
            val methodStack = Stack<Method>()
            val result = mutableListOf<MethodInfo>()
            var previousBlock: BlockInfo? = null
            for (action in actions) {
                when (action) {
                    is MethodEntry -> {
                        methodStack.push(action.method)
                        infos.push(Info(null, arrayOf(), mutableMapOf(), null, null, exception))
                    }
                    is MethodInstance -> {
                        assert(methodStack.peek() == action.method, { log.error("Incorrect action format: Instance action for wrong method") })
                        val info = infos.peek()
                        info.instance = action.instance.rhv
                    }
                    is MethodArgs -> {
                        assert(methodStack.peek() == action.method, { log.error("Incorrect action format: Arg action for wrong method") })
                        val info = infos.peek()
                        info.args = action.args.map { it.rhv }.toTypedArray()
                    }
                    is MethodReturn -> {
                        assert(methodStack.peek() == action.method, { log.error("Incorrect action format: Return action for wrong method") })
                        val info = infos.peek()
                        info.retval = action.`return`?.rhv

                        result.add(MethodInfo(methodStack.pop(), info.instance, info.args, info.blocks, info.retval, info.throwval, info.exception))
                        infos.pop()
                    }
                    is MethodThrow -> {
                        assert(methodStack.peek() == action.method, { log.error("Incorrect action format: Throw action for wrong method") })
                        val info = infos.peek()
                        info.throwval = action.throwable.rhv

                        result.add(MethodInfo(methodStack.pop(), info.instance, info.args, info.blocks, info.retval, info.throwval, info.exception))
                        infos.pop()
                    }
                    is BlockEntryAction -> {
                        val bb = action.bb
                        assert(methodStack.peek() == bb.parent, { log.error("Incorrect action format: Block action for wrong method") })
                        val info = infos.peek()

                        val bInfo = BlockInfo(bb, previousBlock)
                        previousBlock = bInfo
                        info.blocks.getOrPut(bb, { mutableListOf() }).add(bInfo)
                    }
                    is BlockExitAction -> {
                        val bb = action.bb
                        assert(methodStack.peek() == bb.parent, { log.error("Incorrect action format: Block action for wrong method") })
                        assert(previousBlock != null, { log.error("Incorrect action format: Block exit without entering") })
                        previousBlock?.outputAction = action
                    }
                }
            }
            while (methodStack.isNotEmpty()) {
                val info = infos.peek()
                result.add(MethodInfo(methodStack.pop(), info.instance, info.args, info.blocks, info.retval, info.throwval, info.exception))
                infos.pop()
            }
            return result
        }
    }
}

object CoverageManager {
    private val methods = mutableMapOf<Method, MutableList<MethodInfo>>()

    fun getMethodInfos(method: Method): List<MethodInfo> = methods.getOrPut(method, { mutableListOf() })
    fun getBlockInfos(bb: BasicBlock): List<BlockInfo> = if (bb.parent != null) getMethodInfos(bb.parent!!).map {
        it.blocks[bb] ?: listOf()
    }.flatten() else listOf()

    fun addInfo(method: Method, info: MethodInfo) = methods.getOrPut(method, { mutableListOf() }).add(info)

    fun isCovered(bb: BasicBlock) = getBlockInfos(bb).fold(false, { acc, it -> acc or it.hasOutput() })
    fun isBodyCovered(method: Method): Boolean {
        val bodyBLocks = method.getBodyBlocks()
        var res = true
        for (it in bodyBLocks) {
            val cov = isCovered(it)
            res = res and cov
        }
        return res
    }// = method.getBodyBlocks().map { isCovered(it) }.fold(true, { acc, it -> acc and it })
    fun isCatchCovered(method: Method) = method.getCatchBlocks().map { isCovered(it) }.fold(true, { acc, it -> acc and it })
    fun isFullCovered(method: Method) = isBodyCovered(method) and isCatchCovered(method)
}