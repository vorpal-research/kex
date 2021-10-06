package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kex.asm.manager.BlockWrapper
import org.jetbrains.research.kex.asm.manager.MethodWrapper
import org.jetbrains.research.kex.asm.manager.ValueWrapper
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value


sealed class Action

sealed class MethodAction(val method: MethodWrapper) : Action()
class MethodEntry(method: MethodWrapper, val instance: Any?, val args: Array<Any?>) : MethodAction(method) {
    override fun toString() = "Enter $method"
}

class MethodReturn(method: MethodWrapper, val block: BlockWrapper, val returnValue: Any?) : MethodAction(method) {
    override fun toString() = "Return from $method"
}

class MethodThrow(method: MethodWrapper, val block: BlockWrapper, val throwable: Throwable) : MethodAction(method) {
    override fun toString() = "Throw from $method"
}

class MethodCall(method: MethodWrapper, val returnValue: ValueWrapper?, val instance: ValueWrapper?, val args: Array<ValueWrapper>) :
    MethodAction(method) {
    override fun toString() = "Call $method"
}

class StaticInitEntry(method: MethodWrapper) : MethodAction(method) {
    override fun toString() = "Enter $method"
}

class StaticInitExit(method: MethodWrapper) : MethodAction(method) {
    override fun toString() = "Exit $method"
}

sealed class BlockAction(val block: BlockWrapper) : Action()
class BlockEntry(bb: BlockWrapper) : BlockAction(bb) {
    override fun toString() = "Enter ${block.name}"
}

class BlockJump(bb: BlockWrapper) : BlockAction(bb) {
    override fun toString() = "Jump from ${block.name}"
}

class BlockBranch(bb: BlockWrapper, val condition: Any?, val expected: Any?) : BlockAction(bb) {
    override fun toString() = "Branch from ${block.name}"
}

class BlockSwitch(bb: BlockWrapper, val key: Any?) : BlockAction(bb) {
    override fun toString() = "Switch from ${block.name}"
}