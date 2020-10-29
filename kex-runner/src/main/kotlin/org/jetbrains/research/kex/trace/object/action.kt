package org.jetbrains.research.kex.trace.`object`

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value


sealed class Action {
    open infix fun formalEquals(other: Action): Boolean = TODO("Undefined")
}

sealed class MethodAction(val method: Method) : Action() {
    override fun formalEquals(other: Action): Boolean {
        if (other is MethodAction)
            return this.method == other.method
        return false
    }
}


class MethodEntry(method: Method, val instance: Any?, val args: Array<Any?>) : MethodAction(method) {
    override fun formalEquals(other: Action): Boolean {
        if (other is MethodEntry)
            return super.formalEquals(other) && this.args contentDeepEquals other.args
        return false
    }

    override fun toString() = "Enter $method"
}

class MethodReturn(method: Method, val block: BasicBlock, val returnValue: Any?) : MethodAction(method) {
    override fun formalEquals(other: Action): Boolean {
        if (other is MethodReturn)
            return super.formalEquals(other) && this.block == other.block && this.returnValue == other.returnValue
        return false
    }

    override fun toString() = "Return from $method"
}

class MethodThrow(method: Method, val block: BasicBlock, val throwable: Throwable) : MethodAction(method) {
    override fun formalEquals(other: Action): Boolean {
        if (other is MethodThrow)
            return super.formalEquals(other) && this.block == other.block && this.throwable == other.throwable
        return false
    }

    override fun toString() = "Throw from $method"
}

class MethodCall(method: Method, val returnValue: Value?, val instance: Value?, val args: Array<Value>) : MethodAction(method) {
    override fun formalEquals(other: Action): Boolean {
        if (other is MethodCall)
            return super.formalEquals(other) && this.returnValue == other.returnValue && this.args contentDeepEquals other.args
        return false
    }

    override fun toString() = "Call $method"
}

class StaticInitEntry(method: Method) : MethodAction(method) {
    override fun toString() = "Enter $method"
}

class StaticInitExit(method: Method) : MethodAction(method) {
    override fun toString() = "Exit $method"
}

sealed class BlockAction(val block: BasicBlock) : Action() {
    override fun formalEquals(other: Action): Boolean {
        if (other is BlockAction)
            return this.block == other.block
        return false
    }
}

class BlockEntry(bb: BasicBlock) : BlockAction(bb) {
    override fun toString() = "Enter ${block.name}"

    override fun formalEquals(other: Action): Boolean {
        if (other is BlockEntry)
            return super.formalEquals(other)
        return false
    }
}

class BlockJump(bb: BasicBlock) : BlockAction(bb) {
    override fun toString() = "Jump from ${block.name}"

    override fun formalEquals(other: Action): Boolean {
        if (other is BlockJump)
            return super.formalEquals(other)
        return false
    }
}

class BlockBranch(bb: BasicBlock, val condition: Any?, val expected: Any?) : BlockAction(bb) {
    override fun toString() = "Branch from ${block.name}"

    override fun formalEquals(other: Action): Boolean {
        if (other is BlockBranch)
            return super.formalEquals(other)
        return false
    }
}

class BlockSwitch(bb: BasicBlock, val key: Any?) : BlockAction(bb) {
    override fun toString() = "Switch from ${block.name}"

    override fun formalEquals(other: Action): Boolean {
        if (other is BlockSwitch)
            return super.formalEquals(other)
        return false
    }
}