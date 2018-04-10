package org.jetbrains.research.kex.runner

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser
import org.jetbrains.research.kex.UnexpectedTypeException
import org.jetbrains.research.kex.UnknownNameException
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseStringToType
import org.jetbrains.research.kfg.util.defaultHashCode
import java.util.*

interface ActionValue
object NullValue : ActionValue {
    override fun toString() = "null"
}

class KfgValue(val value: Value) : ActionValue {
    override fun hashCode() = defaultHashCode(value)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as KfgValue
        return this.value == other.value
    }

    override fun toString() = value.toString()
}

class BooleanValue(val value: Boolean) : ActionValue {
    override fun hashCode() = defaultHashCode(value)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as BooleanValue
        return this.value == other.value
    }

    override fun toString() = value.toString()
}

class LongValue(val value: Long) : ActionValue {
    override fun hashCode() = defaultHashCode(value)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as LongValue
        return this.value == other.value
    }

    override fun toString() = value.toString()
}

class DoubleValue(val value: Double) : ActionValue {
    override fun hashCode() = defaultHashCode(value)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as DoubleValue
        return this.value == other.value
    }

    override fun toString() = value.toString()
}

class StringValue(val value: String) : ActionValue {
    override fun hashCode() = defaultHashCode(value)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as StringValue
        return this.value == other.value
    }

    override fun toString() = value
}

class ArrayValue(val identifier: Int, val component: Type, val length: Int) : ActionValue {
    override fun hashCode() = identifier
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ArrayValue
        return this.identifier == other.identifier
    }

    override fun toString() = "array@$identifier{$component, $length}"
}

class ObjectValue(val type: Class, val identifier: Int, val fields: Map<String, ActionValue>) : ActionValue {
    override fun hashCode() = identifier
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ObjectValue
        return this.identifier == other.identifier
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$type@$identifier {")
        val fieldNames = fields.keys
        val fieldValues = fields.values.toList()
        fieldNames.withIndex().take(1).forEach { (indx, name) ->
            sb.append("$name = ")
            sb.append(fieldValues[indx].toString())
        }
        fieldNames.withIndex().drop(1).forEach { (indx, name) ->
            sb.append(", $name = ")
            sb.append(fieldValues[indx].toString())
        }
        sb.append("}")
        return sb.toString()
    }
}

class Equation(val lhv: ActionValue, val rhv: ActionValue) {
    override fun toString() = "$lhv == $rhv"
}

interface Action
abstract class MethodAction(val method: Method) : Action
abstract class MethodEntryAction(method: Method) : MethodAction(method)
abstract class MethodExitAction(method: Method) : MethodAction(method)

abstract class BlockAction(val bb: BasicBlock) : Action
abstract class BlockEntryAction(bb: BasicBlock) : BlockAction(bb)
abstract class BlockExitAction(bb: BasicBlock) : BlockAction(bb)

class MethodEntry(method: Method) : MethodEntryAction(method) {
    override fun toString() = "enter $method;"
}

class MethodInstance(method: Method, val instance: Equation) : MethodEntryAction(method) {
    override fun toString() = "instance $method; $instance;"
}

class MethodArgs(method: Method, val args: List<Equation>) : MethodEntryAction(method) {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("arguments $method;")
        args.forEach { sb.append(" $it;") }
        return sb.toString()
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
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("branch ${bb.name};")
        conditions.forEach { sb.append(" $it;") }
        return sb.toString()
    }
}

class BlockSwitch(bb: BasicBlock, val key: Equation) : BlockExitAction(bb) {
    override fun toString() = "exit ${bb.name}; $key;"
}

class BlockTableSwitch(bb: BasicBlock, val key: Equation) : BlockExitAction(bb) {
    override fun toString() = "exit ${bb.name}; $key;"
}

class ActionParser : Grammar<Action>() {
    var trackers = Stack<SlotTracker>()

    fun getTracker() = trackers.peek() ?: throw UnknownNameException("No slot tracker defined")

    // keyword tokens
    val `this` by token("this")
    val `throw` by token("throw")
    val exit by token("exit")
    val `return` by token("return")
    val enter by token("enter")
    val branch by token("branch")
    val switch by token("switch")
    val tableswitch by token("tableswitch")
    val `true` by token("true")
    val `false` by token("false")
    val `null` by token("null")
    val array by token("array")
    val arguments by token("arguments")
    val instance by token("instance")
    val arg by token("arg\\$")

    val keyword by `this` or
            `throw` or
            `return` or
            exit or
            enter or
            branch or
            switch or
            tableswitch or
            `true` or
            `false` or
            `null` or
            arg

    // symbol tokens
    val space by token("\\s+")
    val dot by token("\\.")
    val equality by token("==")
    val openCurlyBrace by token("\\{")
    val closeCurlyBrace by token("\\}")
    val openSquareBrace by token("\\[")
    val closeSquareBrace by token("\\]")
    val openBracket by token("\\(")
    val closeBracket by token("\\)")
    val percent by token("\\%")
    val colon by token(":")
    val semicolon by token(";")
    val comma by token(",")
    val minus by token("-")
    val doubleNum by token("\\d+\\.\\d+")
    val num by token("\\d+")
    val word by token("[a-zA-Z][\\w$]*")
    val at by token("@")
    val string by token("\"[\\w\\s\\.@>=<]*\"")

    val colonAndSpace by colon and space
    val semicolonAndSpace by semicolon and space
    val commaAndSpace by comma and space
    val spacedSemicolon by semicolon and optional(space)

    val anyWord by (word use { text }) or
            ((keyword and optional(num)) use { t1.text + (t2?.text ?: "") })

    // equation
    val valueName by ((`this` use { text }) or
            ((arg and num) use { t1.text + t2.text }) or
            ((percent and anyWord and optional(-dot and separatedTerms(anyWord, dot))) use {
                t1.text + t2 + (t3?.fold("", { acc, curr -> "$acc.$curr" }) ?: "")
            }) or
            ((percent and num) use { t1.text + t2.text })
            ) use {
        getTracker().getValue(this) ?: throw UnknownNameException("Undefined name $this")
    }

    val blockName by (percent and anyWord and optional(-dot and separatedTerms(anyWord, dot))) use {
        val name = t1.text + t2 + (t3?.fold("", { acc, curr -> "$acc.$curr" }) ?: "")
        getTracker().getBlock(name) ?: throw UnknownNameException("Undefined name $name")
    }

    val typeName by (separatedTerms(word, dot) use { map { it.text } } and zeroOrMore(openSquareBrace and closeSquareBrace)) use {
        val braces = t2.fold("", { acc, it -> "$acc${it.t1.text}${it.t2.text}" })
        val typeName = t1.fold("", { acc, curr -> "$acc/$curr" }).drop(1)
        parseStringToType("$typeName$braces")
    }
    val args by separatedTerms(typeName, commaAndSpace)
    val methodName by ((separatedTerms(word, dot) use { map { it.text } }) and
            -openBracket and optional(args) and -closeBracket and
            -colonAndSpace and
            typeName) use {
        val `class` = CM.getByName(t1.dropLast(1).fold("", { acc, curr -> "$acc/$curr" }).drop(1))
        val methodName = t1.takeLast(1).firstOrNull() ?: throw UnknownNameException("Undefined method $t1")
        val args = t2?.toTypedArray() ?: arrayOf()
        val rettype = t3
        `class`.getMethod(methodName, MethodDesc(args, rettype))
    }

    val kfgValueParser by valueName use { KfgValue(this) }
    val nullValueParser by `null` use { NullValue }
    val booleanValueParser by (`true` or `false`) use { BooleanValue(text.toBoolean()) }
    val longValueParser by (optional(minus) and num) use {
        LongValue(((t1?.text ?: "") + t2.text).toLong())
    }
    val doubleValueParser by (optional(minus) and doubleNum) use {
        DoubleValue(((t1?.text ?: "") + t2.text).toDouble())
    }
    val stringValueParser by string use { StringValue(text.drop(1).dropLast(1)) }
    val arrayValueParser by (-array and -at and num and -openCurlyBrace and typeName and -commaAndSpace and num and -closeCurlyBrace) use {
        ArrayValue(t1.text.toInt(), t2, t3.text.toInt())
    }
    val objectValueParser: Parser<ActionValue> by (typeName and -at and num and
            -openCurlyBrace and
            optional(separatedTerms(word and -space and -equality and -space and parser(this::valueParser), commaAndSpace)) and
            -closeCurlyBrace) use {
        val type = (t1 as? ClassType)?.`class` ?: throw UnexpectedTypeException("Unexpected class type $t1")
        val identifier = t2.text.toInt()
        val fields = t3?.map { it.t1.text to it.t2 }?.toMap() ?: mapOf()
        ObjectValue(type, identifier, fields)
    }

    val valueParser by kfgValueParser or
            nullValueParser or
            booleanValueParser or
            longValueParser or
            doubleValueParser or
            stringValueParser or
            arrayValueParser or
            objectValueParser

    val equationParser by (valueParser and -space and -equality and -space and valueParser) use { Equation(t1, t2) }
    val equationList by separatedTerms(equationParser, semicolonAndSpace)

    // action
    val methodEntryParser by (-enter and -space and methodName and -spacedSemicolon) use {
        trackers.push(this.slottracker)
        MethodEntry(this)
    }

    val methodInstanceParser by (-instance and -space and methodName and -semicolonAndSpace and equationParser and -spacedSemicolon) use {
        MethodInstance(t1, t2)
    }

    val methodArgsParser by (-arguments and -space and methodName and -semicolonAndSpace and equationList and -spacedSemicolon) use {
        MethodArgs(t1, t2)
    }

    val methodReturnParser by (-`return` and -space and methodName and -semicolonAndSpace and ((word use { null }) or equationParser and -spacedSemicolon)) use {
        val ret = MethodReturn(t1, t2)
        trackers.pop()
        ret
    }

    val methodThrowParser by (-`throw` and -space and methodName and -space and equationParser and -spacedSemicolon) use { MethodThrow(t1, t2) }

    val blockEntryParser by (-enter and -space and blockName and -spacedSemicolon) use { BlockEntry(this) }
    val blockJumpParser by (-exit and -space and blockName and -spacedSemicolon) use { BlockJump(this) }
    val blockBranchParser by (-branch and -space and blockName and -semicolonAndSpace and equationList and -spacedSemicolon) use { BlockBranch(t1, t2) }
    val blockSwitchParser by (-switch and -space and blockName and -semicolonAndSpace and equationParser and -spacedSemicolon) use { BlockSwitch(t1, t2) }
    val blockTableSwitchParser by (-tableswitch and -space and blockName and -semicolonAndSpace and equationParser and -spacedSemicolon) use { BlockTableSwitch(t1, t2) }

    override val rootParser by (methodEntryParser or methodInstanceParser or methodArgsParser or methodReturnParser or methodThrowParser or
            blockEntryParser or blockJumpParser or blockBranchParser or blockSwitchParser or blockTableSwitchParser)
}