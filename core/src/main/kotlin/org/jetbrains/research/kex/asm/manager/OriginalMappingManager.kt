package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

var Method.original
    get() = OriginalMappingManager.methodMapping[this]
    set(value) {
        if (value != null) OriginalMappingManager.methodMapping[this] = value
    }

var BasicBlock.original
    get() = OriginalMappingManager.blockMapping[this]
    set(value) {
        if (value != null) OriginalMappingManager.blockMapping[this] = value
    }

object OriginalMappingManager {
    val methodMapping = mutableMapOf<Method, Method>()
    val blockMapping = mutableMapOf<BasicBlock, BasicBlock>()
}