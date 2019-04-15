package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term

@AnnotationFunctionality("org.jetbrains.annotations.Range")
class Range(val from: Long, val to: Long) : AnnotationInfo() {
    override fun valuePrecise(value: Term) = assume { (from..to has value) equality true }
}

@AnnotationFunctionality("org.jetbrains.annotations.NotNull")
class NotNull : AnnotationInfo() {
    override fun valuePrecise(value: Term) = assume { value inequality null }
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

    override fun callPreciseAfter(predicate: CallPredicate) = build {
        if (value.isBlank())
            return@build
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
                var leftTerm by value<Boolean>("%contract#$id.$recordN.$i")
                val rightTerm = when (record.params[i]) {
                    Constraints.Null -> args[i] eq const(null)
                    Constraints.NotNull -> args[i] neq const(null)
                    Constraints.True -> args[i] eq const(true)
                    Constraints.False -> args[i] eq const(false)
                    Constraints.Fail, Constraints.New -> throw IllegalStateException("The " +
                            "${record.params[i].literal} constraint value may be interpreted as effect only")
                    Constraints.Any -> throw RuntimeException("Can not be there")
                }
                leftTerm = rightTerm
                termsAnd += leftTerm
            }
            for (i in 1 until termsAnd.size) {
                termsAnd[i] load (termsAnd[i-1] and termsAnd[i])
            }
            var effectTerm by value<Boolean>("%contract#$id.$recordN.effect")
            val rightTerm = when (record.result) {
                Constraints.Null -> returnTerm eq const(null)
                Constraints.NotNull, Constraints.New -> returnTerm neq const(null)
                Constraints.True -> returnTerm eq const(true)
                Constraints.False -> returnTerm eq const(false)
                Constraints.Fail -> const(true) // TODO: There should be something useful
                Constraints.Any -> throw IllegalStateException("Contract effect should be specified")
            }
            effectTerm = rightTerm
            val last = termsAnd.last()
            effectTerm = effectTerm and last
            termsOrParams += last
            termsOr += effectTerm as Term
            termsAnd.clear()
        }
        for (i in 1 until termsOr.size) {
            termsOr[i] load (termsOr[i-1] or termsOr[i])
        }
        var contractSituation by value<Boolean>("%contract#$id.fit")
        contractSituation = termsOrParams.firstOrNull() ?: return@build
        for (i in 1 until termsOrParams.size) {
            contractSituation = contractSituation or termsOrParams[i]
        }
        contractSituation = not(contractSituation) or termsOr.last()
        assumeTrue { contractSituation }
    }
}
