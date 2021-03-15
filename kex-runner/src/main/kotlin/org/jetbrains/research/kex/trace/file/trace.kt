package org.jetbrains.research.kex.trace.file

import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.error
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import java.util.*

data class BlockInfo internal constructor(
        val bb: BasicBlock,
        val predecessor: BlockInfo?,
        internal var outputAction: BlockExitAction? = null) {

    val hasPredecessor: Boolean
        get() = predecessor != null

    val hasOutput: Boolean
        get() = outputAction != null

    override fun toString() = buildString {
        append("enter ${bb.name}")
        outputAction?.let { append(it.toString()) }
    }
}

data class Trace constructor(val method: Method,
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
                        previousBlock.outputAction = action
                    }
                }
            }
            ktassert(methodStack.size == infos.size)
            run {
                while (methodStack.isNotEmpty()) {
                    result.add(methodStack.pop() to infos.pop())
                }
            }
            ktassert(result.isNotEmpty())
            return result.map { it.second.toTrace(it.first) }.reduceRight { trace, acc ->
                trace.copy(subtraces = trace.subtraces + acc)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Trace) return false

        if (method != other.method) return false
        if (instance != other.instance) return false
        if (!args.contentEquals(other.args)) return false
        if (blocks != other.blocks) return false
        if (retval != other.retval) return false
        if (throwable != other.throwable) return false
        if (exception != other.exception) return false
        if (subtraces != other.subtraces) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + (instance?.hashCode() ?: 0)
        result = 31 * result + args.contentHashCode()
        result = 31 * result + blocks.hashCode()
        result = 31 * result + (retval?.hashCode() ?: 0)
        result = 31 * result + (throwable?.hashCode() ?: 0)
        result = 31 * result + (exception?.hashCode() ?: 0)
        result = 31 * result + subtraces.hashCode()
        return result
    }
}