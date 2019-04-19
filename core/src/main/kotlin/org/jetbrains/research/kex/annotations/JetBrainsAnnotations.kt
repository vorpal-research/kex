package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kex.ktype.KexBool
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.util.unreachable

@AnnotationFunctionality("org.jetbrains.annotations.Range")
class Range(val from: Long, val to: Long) : AnnotationInfo() {
    override fun preciseValue(value: Term) = assume { (from..to has value) equality true }
}

@AnnotationFunctionality("org.jetbrains.annotations.NotNull")
class NotNull : AnnotationInfo() {
    override fun preciseValue(value: Term) = assume { value inequality null }
}

@AnnotationFunctionality("org.jetbrains.annotations.Nullable")
class Nullable : AnnotationInfo()

@AnnotationFunctionality("org.jetbrains.annotations.Contract")
class Contract(val value: String = ""/*, pure: Boolean = false*/) : AnnotationInfo() {

    private enum class Constraints(val literal: String) {
        Any("_"),     Null("null"),   NotNull("!null"),
        True("true"), False("false"), Fail("fail"),
        New("new"),   This("this"),   Param("param");

        companion object {
            private val byLiteral = values().associateBy { it.literal }
            operator fun get(literal: String) = byLiteral[literal]
                    ?: throw AnnotationParserException("Unsupported value constraint \"$literal\"")
        }
    }

    private class Record(val params: List<Constraints>, val result: Constraints, val meta: Int = -1)
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
                records += if (resultLiteral.startsWith("param")) {
                    val i = resultLiteral.substring(5).toInt()
                    Record(args, Constraints.Param, i - 1)
                } else
                    Record(args, Constraints[resultLiteral])
            }
        }
        // Find errors
        for (record in records) {
            if (record.result == Constraints.Any)
                throw IllegalStateException("Contract effect should be specified")
            for (param in record.params) {
                when (param) {
                    in effects2, Constraints.New, Constraints.Fail ->
                        throw IllegalStateException("Constraint ${param.literal} is an effect")
                    else -> {}
                }
            }
        }
    }

    private fun getTermByConstraint(constraint: Constraints, arg: Term) = term {
        when (constraint) {
            Constraints.Any -> const(true)
            Constraints.Null -> arg eq null
            Constraints.NotNull -> arg neq null
            Constraints.True -> arg eq true
            Constraints.False -> arg eq false
            Constraints.Fail, Constraints.New, Constraints.This, Constraints.Param ->
                throw IllegalStateException("The ${constraint.literal} constraint value may be" +
                        " interpreted as effect only")
        }
    }

    override fun preciseBeforeCall(predicate: CallPredicate) = basic {
        // Find fail situations
        val call = predicate.call as CallTerm
        val args = call.arguments
        for (record in records.asSequence().filter { it.result == Constraints.Fail }) {
            val params = record.params
            var accumulator: Term = const(true)
            for (i in 0 until params.size) {
                if (params[i] == Constraints.Any)
                    continue
                accumulator = accumulator and getTermByConstraint(params[i], args[i])
            }
            assumeFalse { accumulator }
        }
    }

    private companion object {
        private var count = 0
        val id get() = count++
        val effects1 = arrayOf(Constraints.Null, Constraints.NotNull, Constraints.True,
                Constraints.False, Constraints.New)
        val effects2 = arrayOf(Constraints.This, Constraints.Param)
    }

    override fun preciseAfterCall(predicate: CallPredicate): PredicateState? {
        val call = predicate.call as CallTerm
        val args = call.arguments
        val returnTerm = predicate.getLhvUnsafe() ?: return null
        val id = id.toString()
        // Make boolean functions for same cases
        var result: PredicateState = basic {
            if (!records.any { it.result in effects1 })
                return@basic
            for (record in records.asSequence().filter { it.result in effects1 }) {
                val params = record.params
                val effect = when (record.result) {
                    Constraints.Null -> returnTerm eq const(null)
                    Constraints.NotNull, Constraints.New -> returnTerm neq const(null)
                    Constraints.True -> returnTerm eq const(true)
                    Constraints.False -> returnTerm eq const(false)
                    else -> unreachable { record.result }
                }
                var argsCheck: Term? = null
                for (i in 0 until params.size) {
                    if (params[i] == Constraints.Any)
                        continue
                    if (argsCheck == null)
                        argsCheck = getTermByConstraint(params[i], args[i])
                    else
                        argsCheck = argsCheck and getTermByConstraint(params[i], args[i])
                }
                val check = if (argsCheck != null)
                    not(argsCheck) or effect
                else
                    effect
                assumeTrue { check }
            }
        }
        if (records.any { it.result in effects2 }) {
            for ((i, record) in records.asSequence().withIndex().filter { it.value.result in effects2 }) {
                val params = record.params
                val argUnion = term { value(KexBool, "%contract$id.$i.args") }
                result += basic {
                    var accumulator: Term = const(true)
                    for (j in 0 until params.size) {
                        if (params[j] != Constraints.Any)
                            accumulator = accumulator and getTermByConstraint(params[j], args[j])
                    }
                    argUnion load accumulator
                } + choice(basic {
                    path { argUnion equality true }
                    assume {
                        returnTerm equality when (record.result) {
                            Constraints.This -> call.owner
                            Constraints.Param -> args[record.meta]
                            else -> unreachable { record.result }
                        }
                    }
                }, basic { path { argUnion equality false } } )
            }
        }
        return result
    }
}
