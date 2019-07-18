package org.jetbrains.research.kex.trace

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.MismatchedToken
import com.github.h0tk3y.betterParse.parser.Parser
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseStringToType
import java.util.*

abstract class ActionParseException(msg: String) : Exception(msg)

class UnknownTypeException(msg: String) : ActionParseException(msg)

class UnknownNameException(msg: String) : ActionParseException(msg)


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

interface Action

sealed class MethodAction(val method: Method) : Action
sealed class MethodEntryAction(method: Method) : MethodAction(method)
sealed class MethodExitAction(method: Method) : MethodAction(method)

sealed class BlockAction(val bb: BasicBlock) : Action
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

class ActionParser(val cm: ClassManager) : Grammar<Action>() {
    private var trackers = Stack<SlotTracker>()

    private val tracker: SlotTracker
        get() = trackers.peek() ?: unreachable { log.error("No slot trackers defined") }

    // keyword tokens
    private val `this` by token("this")
    private val `throw` by token("throw")
    private val exit by token("exit")
    private val `return` by token("return")
    private val enter by token("enter")
    private val branch by token("branch")
    private val switch by token("switch")
    private val tableswitch by token("tableswitch")
    private val `true` by token("true")
    private val `false` by token("false")
    private val `null` by token("null")
    private val array by token("array")
    private val arguments by token("arguments")
    private val instance by token("instance")
    private val arg by token("arg\\$")
    private val nan by token("NaN")

    private val keyword by `this` or
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
            instance or
            array or
            arguments or
            arg

    // symbol tokens
    private val space by token("\\s+")
    private val dot by token("\\.")
    private val equality by token("==")
    private val openCurlyBrace by token("\\{")
    private val closeCurlyBrace by token("}")
    private val openSquareBrace by token("\\[")
    private val closeSquareBrace by token("]")
    private val openBracket by token("\\(")
    private val closeBracket by token("\\)")
    private val percent by token("%")
    private val colon by token(":")
    private val semicolon by token(";")
    private val comma by token(",")
    private val minus by token("-")
    private val doubleNum by token("\\d+\\.\\d+(E(-)?\\d+)?")
    private val num by token("\\d+")
    private val word by token("[a-zA-Z$][\\w$]*")
    private val character by token("[a-zA-Z]")
    private val at by token("@")
    private val string by token("\"[\\w\\sа-яА-ЯёЁ\\-.@>=<+*,\\(\\):\\[\\]]*\"")

    private val colonAndSpace by colon and space
    private val semicolonAndSpace by semicolon and space
    private val commaAndSpace by comma and space
    private val spacedSemicolon by semicolon and optional(space)

    private val anyWord by (word use { text }) or
            ((keyword and word) use { t1.text + t2.text }) or
            ((keyword and num) use { t1.text + t2.text }) or
            (keyword use { text })

    // equation
    private val valueName by (
            (`this` use { text }) or
                    ((arg and num) use { t1.text + t2.text }) or
                    ((percent and anyWord and optional(-dot and separatedTerms(anyWord, dot))) use {
                        t1.text + t2 + (t3?.fold("") { acc, curr -> "$acc.$curr" } ?: "")
                    }) or
                    ((percent and num) use { t1.text + t2.text })
            ) use {
        tracker.getValue(this) ?: throw UnknownNameException(this)
    }

    private val blockName by (percent and anyWord and optional(-dot and separatedTerms(anyWord, dot))) use {
        val name = t1.text + t2 + (t3?.fold("") { acc, curr -> "$acc.$curr" } ?: "")
        tracker.getBlock(name) ?: throw UnknownNameException(name)
    }

    private val typeName by (separatedTerms(word, dot) use { map { it.text } }
            and zeroOrMore(openSquareBrace and closeSquareBrace)) use {
        val braces = t2.fold("") { acc, it -> "$acc${it.t1.text}${it.t2.text}" }
        val typeName = t1.fold("") { acc, curr -> "$acc/$curr" }.drop(1)
        parseStringToType(cm.type, "$typeName$braces")
    }
    private val args by separatedTerms(typeName, commaAndSpace, true)
    private val methodName by ((separatedTerms(word, dot) use { map { it.text } }) and
            -openBracket and args and -closeBracket and
            -colonAndSpace and
            typeName) use {
        val `class` = cm.getByName(t1.dropLast(1).fold("") { acc, curr -> "$acc/$curr" }.drop(1))
        val methodName = t1.takeLast(1).firstOrNull() ?: throw UnknownNameException(t1.toString())
        val args = t2.toTypedArray()
        val rettype = t3
        `class`.getMethod(methodName, MethodDesc(args, rettype))
    }

    private val kfgValueParser by valueName use { KfgValue(this) }
    private val nullValueParser by `null` use { NullValue }
    private val booleanValueParser by (`true` or `false`) use { BooleanValue(text.toBoolean()) }
    private val charValueParser by word use { CharValue(text[0]) }

    private val longValueParser by (optional(minus) and num) use {
        LongValue(((t1?.text ?: "") + t2.text).toLong())
    }
    private val doubleValueParser by ((optional(minus) and doubleNum) use {
        DoubleValue(((t1?.text ?: "") + t2.text).toDouble())
    } or nan use { DoubleValue(Double.NaN) })

    private val stringValueParser by string use { StringValue(text.drop(1).dropLast(1)) }
    private val arrayValueParser by (-array and -at and num and -openCurlyBrace and typeName and -commaAndSpace
            and num and -closeCurlyBrace) use {
        ArrayValue(t1.text.toInt(), t2, t3.text.toInt())
    }
    private val objectFields by separatedTerms(anyWord and -space and -equality and -space
            and parser(this::valueParser), commaAndSpace, true) use {
        map { it.t1 to it.t2 }.toMap()
    }
    private val objectValueParser: Parser<ActionValue> by (typeName and -at and num and -openCurlyBrace
            and objectFields and -closeCurlyBrace) use {
        val type = (t1 as? ClassType)?.`class` ?: throw UnknownTypeException(t1.toString())
        val identifier = t2.text.toInt()
        val fields = t3
        ObjectValue(type, identifier, fields)
    }

    private val valueParser by kfgValueParser or
            nullValueParser or
            booleanValueParser or
            longValueParser or
            doubleValueParser or
            stringValueParser or
            arrayValueParser or
            objectValueParser or
            charValueParser

    private val equationParser by (valueParser and -space and -equality and -space and valueParser) use { Equation(t1, t2) }
    private val equationList by separatedTerms(equationParser, semicolonAndSpace)

    // action
    private val methodEntryParser by (-enter and -space and methodName and -spacedSemicolon) use {
        trackers.push(this.slottracker)
        MethodEntry(this)
    }

    private val methodInstanceParser by (-instance and -space and methodName and -semicolonAndSpace and equationParser
            and -spacedSemicolon) use {
        MethodInstance(t1, t2)
    }

    private val methodArgsParser by (-arguments and -space and methodName and -semicolonAndSpace and equationList
            and -spacedSemicolon) use {
        MethodArgs(t1, t2)
    }

    private val methodReturnParser by (-`return` and -space and methodName
            and -semicolonAndSpace and ((word use { null }) or equationParser and -spacedSemicolon)) use {
        val ret = MethodReturn(t1, t2)
        trackers.pop()
        ret
    }

    private val methodThrowParser by (
            -`throw` and -space and methodName and -semicolonAndSpace and equationParser and -spacedSemicolon) use {
        MethodThrow(t1, t2)
    }

    private val blockEntryParser by (-enter and -space and blockName and -spacedSemicolon) use { BlockEntry(this) }
    private val blockJumpParser by (-exit and -space and blockName and -spacedSemicolon) use { BlockJump(this) }

    private val blockBranchParser by (-branch and -space and blockName and -semicolonAndSpace
            and equationList and -spacedSemicolon) use { BlockBranch(t1, t2) }

    private val blockSwitchParser by (-switch and -space and blockName and -semicolonAndSpace and equationParser
            and -spacedSemicolon) use { BlockSwitch(t1, t2) }

    private val blockTableSwitchParser by (-tableswitch and -space and blockName and -semicolonAndSpace
            and equationParser and -spacedSemicolon) use { BlockTableSwitch(t1, t2) }

    private val actionParser by (methodEntryParser or methodInstanceParser or methodArgsParser or methodReturnParser
            or methodThrowParser or blockEntryParser or blockJumpParser or blockBranchParser or blockSwitchParser or blockTableSwitchParser)

    override val rootParser by actionParser
}