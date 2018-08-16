package org.jetbrains.research.kex.smt.model

import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*

class ModelRecoverer(val method: Method, val model: SMTModel) {
    val tf = TermFactory
    val terms = hashMapOf<Term, Any?>()

    fun apply() {
        val args = method.desc.args.withIndex().map { (index, type) -> tf.getArgument(type, index) }

        for (arg in args) {
            terms[arg] = recover(arg)
        }
    }

    private fun recover(term: Term): Any? = when {
        term.type.isPrimary() -> recoverPrimary(term)
        else -> recoverReference(term)
    }

    private fun recoverPrimary(term: Term): Any? {
        val value = model.assignments[term] ?: return null
        return when (term.type) {
            is BoolType -> (value as ConstBoolTerm).value
            is ByteType -> (value as ConstByteTerm).value
            is CharType -> (value as ConstCharTerm).value
            is ShortType -> (value as ConstShortTerm).value
            is IntType -> (value as ConstIntTerm).value
            is LongType -> (value as ConstLongTerm).value
            is FloatType -> (value as ConstFloatTerm).value
            is DoubleType -> (value as ConstDoubleTerm).value
            else -> unreachable { log.error("Trying to recover non-primary term as primary value") }
        }
    }

    private fun recoverReference(term: Term): Any? = null
}