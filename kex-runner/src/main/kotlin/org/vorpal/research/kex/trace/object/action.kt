package org.vorpal.research.kex.trace.`object`

import org.vorpal.research.kex.asm.manager.BlockWrapper
import org.vorpal.research.kex.asm.manager.MethodWrapper
import org.vorpal.research.kex.asm.manager.ValueWrapper


sealed class Action

sealed class MethodAction(val method: MethodWrapper) : Action()
class MethodEntry(method: MethodWrapper, val instance: Any?, val args: Array<Any?>) : MethodAction(method) {
    override fun toString() = "Enter $method"
}

@Suppress("unused")
class MethodReturn(method: MethodWrapper, val block: BlockWrapper, val returnValue: Any?) : MethodAction(method) {
    override fun toString() = "Return from $method"
}

class MethodThrow(method: MethodWrapper, val block: BlockWrapper, val throwable: Throwable) : MethodAction(method) {
    override fun toString() = "Throw from $method"
}

class MethodCall(
    method: MethodWrapper,
    val returnValue: ValueWrapper?,
    val instance: ValueWrapper?,
    val args: Array<ValueWrapper>
) :
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

@Suppress("unused")
class BlockBranch(bb: BlockWrapper, val condition: Any?, val expected: Any?) : BlockAction(bb) {
    override fun toString() = "Branch from ${block.name}"
}

class BlockSwitch(bb: BlockWrapper, val key: Any?) : BlockAction(bb) {
    override fun toString() = "Switch from ${block.name}"
}
