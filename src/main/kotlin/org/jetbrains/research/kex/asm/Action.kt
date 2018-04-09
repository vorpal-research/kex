package org.jetbrains.research.kex.asm

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.parser.Parser
import org.jetbrains.research.kex.UnknownNameException
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.parseStringToType
import java.util.*

interface ActionValue
object NullValue : ActionValue
class KfgValue(val value: Value) : ActionValue
class BooleanValue(val value: Boolean) : ActionValue
class LongValue(val value: Long) : ActionValue
class DoubleValue(val value: Double) : ActionValue
class StringValue(val value: String) : ActionValue
class ObjectValue(val type: Class, val identifier: Int, val fields: Map<String, ActionValue>) : ActionValue
class Equation(val lhv: ActionValue, val rhv: ActionValue)

interface Action
abstract class MethodAction(val method: Method) : Action
abstract class BlockAction(val bb: BasicBlock) : Action

class MethodEntry(method: Method) : MethodAction(method)
class MethodReturn(method: Method, val `return`: Equation?) : MethodAction(method)
class MethodThrow(method: Method, val throwable: KfgValue) : MethodAction(method)

class BlockEntry(bb: BasicBlock) : BlockAction(bb)
class BlockJump(bb: BasicBlock) : BlockAction(bb)
class BlockBranch(bb: BasicBlock, val conditions: List<Equation>) : BlockAction(bb)
class BlockSwitch(bb: BasicBlock, val key: Equation) : BlockAction(bb)
class BlockTableSwitch(bb: BasicBlock, val key: Equation) : BlockAction(bb)

class ActionParser : Grammar<Action>() {
    var trackers = Stack<SlotTracker>()
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
    // symbol tokens
    val arg by token("arg\\$")
    val space by token("\\s+", ignore = true)
    val dot by token("\\.")
    val equality by token("==")
    val openBrace by token("\\{")
    val closeBrace by token("\\}")
    val openSqBrace by token("\\[")
    val closeSqBrace by token("\\]")
    val openBracket by token("\\(")
    val closeBracket by token("\\)")
    val percent by token("\\%")
    val colon by token(":\\s*")
    val semicolon by token(";\\s*")
    val comma by token(",\\s*")
    val doubleNum by token("-?\\d+\\.\\d+")
    val num by token("-?\\d+")
    val word by token("[\\w$]+")
    val at by token("@")
    val string by token("\"[\\w\\s\\.@\\d>=<]*\"")

    fun getTracker() = trackers.peek() ?: throw UnknownNameException("No slot tracker defined")

    // equation
    val valueName by (
            (`this` use { text }) or
            ((arg and num) use { t1.text + t2.text }) or
            ((percent and optional(word) and num) use { "${t1.text}${t2?.text ?: ""}${t3.text}" })
            ) use {
        getTracker().getValue(this) ?: throw UnknownNameException("Undefined name $this")
    }
    val blockName by (percent and word and optional(-dot and separatedTerms(word, dot))) use {
        val name = t1.text + t2.text + (t3?.fold("", { acc, curr -> "$acc.${curr.text}" }) ?: "")
        getTracker().getBlock(name) ?: throw UnknownNameException("Undefined name $name")
    }

    val typeName by (separatedTerms(word, dot) use { map { it.text } })
    val args by separatedTerms(typeName, comma)
    val methodName by (typeName and -openBracket and optional(args) and -closeBracket and -colon and typeName) use {
        val `class` = CM.getByName(t1.dropLast(1).fold("", { acc, curr -> "$acc/$curr" }).drop(1))
        val methodName = t1.takeLast(1).firstOrNull() ?: throw UnknownNameException("Undefined method $t1")
        val args = t2?.map { parseStringToType(it.fold("", { acc, curr -> "$acc/$curr" }).drop(1)) }?.toTypedArray()
                ?: arrayOf()
        val rettype = parseStringToType(t3.fold("", { acc, curr -> "$acc/$curr" }).drop(1))
        `class`.getMethod(methodName, MethodDesc(args, rettype))
    }

    val kfgValueParser by valueName use { KfgValue(this) }
    val nullValueParser by `null` use { NullValue }
    val booleanValueParser by (`true` or `false`) use { BooleanValue(text.toBoolean()) }
    val longValueParser by num use { LongValue(text.toLong()) }
    val doubleValueParser by doubleNum use { DoubleValue(text.toDouble()) }
    val stringValueParser by string use { StringValue(text.drop(1).dropLast(1)) }
    val objectValueParser: Parser<ActionValue> by (typeName and -at and num and -openBrace and
            optional(separatedTerms(word and -space and -equality and -space and parser(this::valueParser), comma)) and -closeBrace) use {
        val type = CM.getByName(t1.fold("", { acc, curr -> "$acc/$curr" }).drop(1))
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
            objectValueParser

    val equationParser by (valueParser and -space and -equality and -space and valueParser) use { Equation(t1, t2) }
    val equationList by separatedTerms(equationParser, semicolon)


    val methodEntryParser by (-enter and -space and methodName and -semicolon) use {
        trackers.push(this.slottracker)
        MethodEntry(this)
    }
    val methodReturnParser by (-`return` and -space and methodName and -semicolon and ((word use { null }) or equationParser)) use {
        val ret = MethodReturn(t1, t2)
        trackers.pop()
        ret
    }

    val methodThrowParser by (methodName and -space and -`throw` and -space and kfgValueParser) use { MethodThrow(t1, t2) }

    val blockEntryParser by (blockName and -space and -enter and -semicolon) use { BlockEntry(this) }
    val blockJumpParser by (blockName and -space and -exit and -semicolon) use { BlockJump(this) }
    val blockBranchParser by (blockName and -space and -branch and -colon and equationList) use { BlockBranch(t1, t2) }
    val blockSwitchParser by (blockName and -space and -switch and -colon and equationParser) use { BlockSwitch(t1, t2) }
    val blockTableSwitchParser by (blockName and -space and -tableswitch and -colon and equationParser) use { BlockTableSwitch(t1, t2) }

    override val rootParser by (methodEntryParser or methodReturnParser or methodThrowParser or
            blockEntryParser or blockJumpParser or blockBranchParser or blockSwitchParser or blockTableSwitchParser)
}