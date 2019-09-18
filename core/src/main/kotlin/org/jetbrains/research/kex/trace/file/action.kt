package org.jetbrains.research.kex.trace.file

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type


sealed class ActionValue

object NullValue : ActionValue() {
    override fun toString() = "null"
}

data class KfgValue(val value: Value) : ActionValue() {
    override fun toString() = value.toString()
}

data class BooleanValue(val value: Boolean) : ActionValue() {
    override fun toString() = value.toString()
}

data class CharValue(val value: Char) : ActionValue() {
    override fun toString() = value.toString()
}

data class LongValue(val value: Long) : ActionValue() {
    override fun toString() = value.toString()
}

data class DoubleValue(val value: Double) : ActionValue() {
    override fun toString() = value.toString()
}

data class StringValue(val value: String) : ActionValue() {
    override fun toString() = value
}

data class ArrayValue(val identifier: Int, val component: Type, val length: Int) : ActionValue() {
    override fun toString() = "array@$identifier{$component, $length}"
}

data class ObjectValue(val type: Class, val identifier: Int, val fields: Map<String, ActionValue>) : ActionValue() {
    override fun toString() = buildString {
        append("$type@$identifier {")
        val fieldNames = fields.keys
        val fieldValues = fields.values.toList()
        fieldNames.withIndex().take(1).toList().forEach { (indx, name) ->
            append("$name = ")
            append(fieldValues[indx].toString())
        }
        fieldNames.withIndex().drop(1).toList().forEach { (indx, name) ->
            append(", $name = ")
            append(fieldValues[indx].toString())
        }
        append("}")
    }
}

data class Equation(val lhv: ActionValue, val rhv: ActionValue) {
    override fun toString() = "$lhv == $rhv"
}

sealed class Action

sealed class MethodAction(val method: Method) : Action()
sealed class MethodEntryAction(method: Method) : MethodAction(method)
sealed class MethodExitAction(method: Method) : MethodAction(method)

sealed class BlockAction(val bb: BasicBlock) : Action()
sealed class BlockEntryAction(bb: BasicBlock) : BlockAction(bb)
sealed class BlockExitAction(bb: BasicBlock) : BlockAction(bb)

class MethodEntry(method: Method) : MethodEntryAction(method) {
    override fun toString() = "enter $method;"
}

class MethodInstance(method: Method, val instance: Equation) : MethodEntryAction(method) {
    override fun toString() = "instance $method; $instance;"
}

class MethodArgs(method: Method, val args: List<Equation>) : MethodEntryAction(method) {
    override fun toString() = buildString {
        append("arguments $method;")
        args.forEach { append(" $it;") }
    }
}

class MethodReturn(method: Method, val `return`: Equation?) : MethodExitAction(method) {
    override fun toString() = "return $method; ${`return` ?: "void"}"
}

class MethodThrow(method: Method, val throwable: Equation) : MethodExitAction(method) {
    override fun toString() = "throw $method; $throwable"
}

class BlockEntry(bb: BasicBlock) : BlockEntryAction(bb) {
    override fun toString() = "enter ${bb.name};"
}

class BlockJump(bb: BasicBlock) : BlockExitAction(bb) {
    override fun toString() = "exit ${bb.name};"
}

class BlockBranch(bb: BasicBlock, val conditions: List<Equation>) : BlockExitAction(bb) {
    override fun toString() = buildString {
        append("branch ${bb.name};")
        conditions.forEach { append(" $it;") }
    }
}

class BlockSwitch(bb: BasicBlock, val key: Equation) : BlockExitAction(bb) {
    override fun toString() = "exit ${bb.name}; $key;"
}

class BlockTableSwitch(bb: BasicBlock, val key: Equation) : BlockExitAction(bb) {
    override fun toString() = "exit ${bb.name}; $key;"
}