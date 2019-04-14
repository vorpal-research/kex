package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode

@AnnotationFunctionality("org.jetbrains.annotations.Range")
class Range(val from: Long, val to: Long) : AnnotationInfo() {
    override fun valuePrecise(value: Term) = assume { from..to has value }
}

@AnnotationFunctionality("org.jetbrains.annotations.NotNull")
class NotNull : AnnotationInfo() {
    override fun valuePrecise(value: Term) = assume { value neq null }
}

@AnnotationFunctionality("org.jetbrains.annotations.Nullable")
class Nullable : AnnotationInfo()

@AnnotationFunctionality("org.jetbrains.annotations.Contract")
class Contract(val value: String = ""/*, pure: Boolean = false*/) : AnnotationInfo() {

    private companion object {
        private var count = 0
        val id get() = count++
    }

    private enum class Constraints(val literal: String) {
        Any("_"), Null("null"), NotNull("!null"),
        True("true"), False("false"), Fail("fail"), New("new");

        companion object {
            private val byLiteral = values().associateBy { it.literal }
            operator fun get(literal: String) = byLiteral[literal]
                    ?: throw AnnotationParserException("Unsupported value constraint \"$literal\"")
        }
    }

    private class Record(val params: List<Constraints>, val result: Constraints)
    private val records = mutableListOf<Record>()

    override fun initialize(n: Int) {
        val paramN = call.params.size
        if (value.isNotBlank()) {
            for (clause in value.split(';')) {
                val (argsStr, result) = clause.split("->")
                val args =
                    if (argsStr.isBlank())
                        emptyList()
                    else
                        argsStr.split(',').map { Constraints[it.trim()] }
                check(args.size == paramN) { "Parameters count ${args.size} in contract requires to be the " +
                        "same as in the call $paramN" }
                val resultLiteral = result.trim()
                if (resultLiteral.startsWith("param") || resultLiteral == "this")
                    continue // TODO: Very complex situation when parameter equals to result
                records += Record(args, Constraints[resultLiteral])
            }
        }
    }

    override fun callPreciseAfter(predicate: CallPredicate): List<Predicate> {
        if (value.isBlank())
            return emptyList()
        val result = mutableListOf<Predicate>()
        val args = (predicate.call as CallTerm).arguments
        val returnTerm = predicate.lhv
        val id = id.toString()
        val termsOr = mutableListOf<Term>()
        val termsOrParams = mutableListOf<Term>()
        val termsAnd = mutableListOf<Term>()
        for ((recordN, record) in records.withIndex()) {
            val params = record.params
            for (i in 0 until params.size) {
                if (record.params[i] == Constraints.Any)
                    continue
                val leftTerm = tf.getValue(KexBool, "%contract#$id.$recordN.$i")
                termsAnd += leftTerm
                val rightTerm = when (record.params[i]) {
                    Constraints.Null -> tf.getCmp(CmpOpcode.Eq(), args[i], tf.getNull())
                    Constraints.NotNull -> tf.getCmp(CmpOpcode.Neq(), args[i], tf.getNull())
                    Constraints.True -> tf.getCmp(CmpOpcode.Eq(), args[i], tf.getTrue())
                    Constraints.False -> tf.getCmp(CmpOpcode.Eq(), args[i], tf.getFalse())
                    Constraints.Fail, Constraints.New -> throw IllegalStateException("The " +
                            "${record.params[i].literal} constraint value may be interpreted as effect only")
                    Constraints.Any -> throw RuntimeException("Can not be there")
                }
                result += pf.getLoad(leftTerm, rightTerm)
            }
            for (i in 1 until termsAnd.size) {
                result += pf.getLoad(termsAnd[i], tf.getBinary(KexBool, BinaryOpcode.And(), termsAnd[i-1], termsAnd[i]))
            }
            val effectTerm = tf.getValue(KexBool, "%contract#$id.$recordN.effect")
            val rightTerm = when (record.result) {
                Constraints.Null -> tf.getCmp(CmpOpcode.Eq(), returnTerm, tf.getNull())
                Constraints.NotNull, Constraints.New -> tf.getCmp(CmpOpcode.Neq(), returnTerm, tf.getNull())
                Constraints.True -> tf.getCmp(CmpOpcode.Eq(), returnTerm, tf.getTrue())
                Constraints.False -> tf.getCmp(CmpOpcode.Eq(), returnTerm, tf.getFalse())
                Constraints.Fail -> tf.getTrue() // TODO: There should be something useful
                Constraints.Any -> throw IllegalStateException("Contract effect should be specified")
            }
            result += pf.getLoad(effectTerm, rightTerm)
            val last = termsAnd.last()
            result += pf.getLoad(effectTerm, tf.getBinary(KexBool, BinaryOpcode.And(), effectTerm, last))
            termsOrParams += last
            termsOr += effectTerm
            termsAnd.clear()
        }
        for (i in 1 until termsOr.size) {
            result += pf.getLoad(termsOr[i], tf.getBinary(KexBool, BinaryOpcode.Or(), termsOr[i-1], termsOr[i]))
        }
        val contractSituation = tf.getValue(KexBool, "%contract#$id.fit")
        result += pf.getLoad(contractSituation, termsOrParams.firstOrNull() ?: return emptyList())
        for (i in 1 until termsOrParams.size) {
            result += pf.getLoad(contractSituation, tf.getBinary(KexBool, BinaryOpcode.Or(),
                    contractSituation, termsOrParams[i]))
        }
        result += pf.getLoad(contractSituation, tf.getCmp(KexBool, CmpOpcode.Neq(), contractSituation, tf.getTrue()))
        result += pf.getLoad(contractSituation, tf.getBinary(KexBool, BinaryOpcode.Or(), contractSituation, termsOr.last()))
        result += pf.getEquality(contractSituation, tf.getTrue(), PredicateType.Assume())
        return result
    }
}
