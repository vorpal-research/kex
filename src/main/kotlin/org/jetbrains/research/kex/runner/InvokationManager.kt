package org.jetbrains.research.kex.runner

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

data class BlockInfo internal constructor(val bb: BasicBlock, val predecessor: BlockInfo?) {
    internal val outputAction: BlockExitAction? = null

    fun hasPredecessor() = predecessor == null
    fun hasOutput() = outputAction == null
}

class MethodInfo internal constructor(val method: Method, val args: Array<Any?>) {
    internal val blocks = mutableMapOf<BasicBlock, List<BlockInfo>>()

    fun getBlockInfo(bb: BasicBlock) = blocks.getOrPut(bb, { mutableListOf() })

    companion object {
        fun parse(args: Array<Any?>, actions: List<Action>): List<MethodInfo> {
            var current: MethodInfo? = null
            val result = mutableListOf<MethodInfo>()
            for (action in actions) {
                when (action) {
                    is MethodEntry -> {}
                    is MethodInstance -> {}
                    is MethodArgs -> {}
                    is MethodReturn -> {}
                    is MethodThrow -> {}
                    is BlockEntry -> {}
                    is BlockJump -> {}
                    is BlockBranch -> {}
                    is BlockSwitch -> {}
                    is BlockTableSwitch -> {}
                }
            }
            return result
        }
    }
}

object InvokationManager