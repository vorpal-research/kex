package org.vorpal.research.kex.trace.file

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.MethodDescriptor
import org.vorpal.research.kfg.ir.value.NameMapper
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.parseStringToType
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.util.*

abstract class ActionParseException(msg: String) : Exception(msg)

class UnknownTypeException(msg: String) : ActionParseException(msg)

class UnknownNameException(msg: String) : ActionParseException(msg)

class ActionParser(val cm: ClassManager, val ctx: NameMapperContext) : Grammar<Action>() {
    private var trackers = Stack<NameMapper>()

    private val tracker: NameMapper
        get() = trackers.peek() ?: unreachable { log.error("No slot trackers defined") }

    // keyword tokens
    private val `this` by literalToken("this")
    private val `throw` by literalToken("throw")
    private val exit by literalToken("exit")
    private val `return` by literalToken("return")
    private val enter by literalToken("enter")
    private val branch by literalToken("branch")
    private val switch by literalToken("switch")
    private val tableswitch by literalToken("tableswitch")
    private val `true` by literalToken("true")
    private val `false` by literalToken("false")
    private val `null` by literalToken("null")
    private val array by literalToken("array")
    private val arguments by literalToken("arguments")
    private val instance by literalToken("instance")
    private val arg by literalToken("arg$")
    private val nan by literalToken("NaN")

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
    private val space by regexToken("\\s+")
    private val dot by regexToken("\\.")
    private val equality by literalToken("==")
    private val openCurlyBrace by literalToken("{")
    private val closeCurlyBrace by literalToken("}")
    private val openSquareBrace by literalToken("[")
    private val closeSquareBrace by literalToken("]")
    private val openBracket by literalToken("(")
    private val closeBracket by literalToken(")")
    private val percent by literalToken("%")
    private val colon by literalToken(":")
    private val semicolon by literalToken(";")
    private val comma by literalToken(",")
    private val minus by literalToken("-")
    private val doubleNum by regexToken("\\d+\\.\\d+(E(-)?\\d+)?")
    private val num by regexToken("\\d+")
    private val word by regexToken("[a-zA-Z$][\\w$-]*")
    private val at by literalToken("@")

    @Suppress("RegExpRedundantEscape")
    private val string by regexToken("\"[\\w\\sа-яА-ЯёЁ\\-.@>=<+*,'\\(\\):\\[\\]/\\n{}]*\"")

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
        val klass = cm[t1.dropLast(1).fold("") { acc, curr -> "$acc/$curr" }.drop(1)]
        val methodName = t1.takeLast(1).firstOrNull() ?: throw UnknownNameException(t1.toString())
        val args = t2.toTypedArray()
        val rettype = t3
        klass.getMethod(methodName, MethodDescriptor(args, rettype))
    }

    private val kfgValueParser by valueName use { KfgValue(this) }
    private val nullValueParser by `null` use { NullValue }
    private val booleanValueParser by (`true` or `false`) use { BooleanValue(text.toBoolean()) }

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
    private val objectFields by separatedTerms(
        anyWord and -space and -equality and -space
                and parser(this::valueParser), commaAndSpace, true
    ) use {
        associate { it.t1 to it.t2 }
    }
    private val objectValueParser: Parser<ActionValue> by (typeName and -at and num and -openCurlyBrace
            and objectFields and -closeCurlyBrace) use {
        val type = (t1 as? ClassType)?.klass ?: throw UnknownTypeException(t1.toString())
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
            objectValueParser

    private val equationParser by (valueParser and -space and -equality and -space and valueParser) use {
        Equation(
            t1,
            t2
        )
    }
    private val equationList by separatedTerms(equationParser, commaAndSpace)

    // action
    private val methodEntryParser by (-enter and -space and methodName) use {
        trackers.push(ctx.getMapper(this))
        MethodEntry(this)
    }

    private val methodInstanceParser by (-instance and -space and methodName and -commaAndSpace and equationParser) use {
        MethodInstance(t1, t2)
    }

    private val methodArgsParser by (-arguments and -space and methodName and -commaAndSpace and equationList) use {
        MethodArgs(t1, t2)
    }

    private val methodReturnParser by (-`return` and -space and methodName
            and -commaAndSpace and ((word use { null }) or equationParser)) use {
        val ret = MethodReturn(t1, t2)
        trackers.pop()
        ret
    }

    private val methodThrowParser by (
            -`throw` and -space and methodName and -commaAndSpace and equationParser) use {
        MethodThrow(t1, t2)
    }

    private val blockEntryParser by (-enter and -space and blockName) use { BlockEntry(this) }
    private val blockJumpParser by (-exit and -space and blockName) use { BlockJump(this) }

    private val blockBranchParser by (-branch and -space and blockName and -commaAndSpace
            and equationList) use { BlockBranch(t1, t2) }

    private val blockSwitchParser by (-switch and -space and blockName and -commaAndSpace and equationParser) use {
        BlockSwitch(
            t1,
            t2
        )
    }

    private val blockTableSwitchParser by (-tableswitch and -space and blockName and -commaAndSpace
            and equationParser) use { BlockTableSwitch(t1, t2) }

    private val actionParser by (methodEntryParser or methodInstanceParser or methodArgsParser or methodReturnParser
            or methodThrowParser or blockEntryParser or blockJumpParser or blockBranchParser or blockSwitchParser or blockTableSwitchParser)

    override val rootParser by actionParser
}