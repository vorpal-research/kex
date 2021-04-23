package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

var BasicBlock.originalBlock: BasicBlock
    get() = BlockManager.blockMapping[this.parent]?.get(this) ?: this
    set(value) {
        BlockManager.blockMapping.getOrPut(this.parent, ::mutableMapOf)[this] = value
    }

val BasicBlock.isUnreachable: Boolean
    get() = this in (BlockManager.unreachableBlocks[this.parent] ?: hashSetOf())

object BlockManager {
    val blockMapping = hashMapOf<Method, MutableMap<BasicBlock, BasicBlock>>()
    val unreachableBlocks = hashMapOf<Method, MutableSet<BasicBlock>>()
}