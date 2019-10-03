package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method


sealed class Action

sealed class MethodAction(val method: Method) : Action()
class MethodEntry(method: Method, val instance: Any?, val args: Array<Any?>) : MethodAction(method) {
    override fun toString() = "Enter $method"
}
class MethodReturn(method: Method, val block: BasicBlock, val returnValue: Any?) : MethodAction(method) {
    override fun toString() = "Return from $method"
}
class MethodThrow(method: Method, val block: BasicBlock, val throwable: Throwable) : MethodAction(method) {
    override fun toString() = "Throw from $method"
}

sealed class BlockAction(val block: BasicBlock) : Action()
class BlockEntry(bb: BasicBlock) : BlockAction(bb) {
    override fun toString() = "Enter ${block.name}"
}
class BlockJump(bb: BasicBlock) : BlockAction(bb) {
    override fun toString() = "Jump from ${block.name}"
}
class BlockBranch(bb: BasicBlock, val condition: Any?, val expected: Any?) : BlockAction(bb) {
    override fun toString() = "Branch from ${block.name}"
}
class BlockSwitch(bb: BasicBlock, val key: Any?) : BlockAction(bb) {
    override fun toString() = "Switch from ${block.name}"
}