package org.jetbrains.research.kex.smt.model

import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.primaryConstructor

class ModelRecoverer(val method: Method, val model: SMTModel, val loader: ClassLoader) {
    val tf = TermFactory
    val terms = hashMapOf<Term, Any?>()

    val memoryMappings = hashMapOf<Int, Any?>(0 to null)

    fun apply() {
        val recoveringTerms = hashSetOf<Term>()
        recoveringTerms += method.desc.args.withIndex().map { (index, type) -> tf.getArgument(type, index) }

        if (!method.isAbstract) {
            val `this` = tf.getThis(method.`class`)
            recoveringTerms += `this`
        }

        for (term in recoveringTerms) {
            terms[term] = recover(term)
        }
    }

    private fun recover(term: Term): Any? = when {
        Term.isPrimary(term) -> recoverPrimary(term)
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
            else -> unreachable { log.error("Trying to recover non-primary term as primary value: $term with type ${term.type}") }
        }
    }

    private fun recoverReference(term: Term): Any? = when (term.type) {
        is ClassType -> recoverClass(term)
        is ArrayType -> recoverArray(term)
        else -> unreachable { log.error("Trying to recover non-reference term $term with type ${term.type} as reference value") }
    }

    private fun recoverClass(term: Term): Any? {
        val type = term.type as ClassType
        val value = (model.assignments[term] as? ConstIntTerm)?.value ?: return null
        return memoryMappings.getOrPut(value) {
            null
        }
    }

    private fun recoverArray(term: Term): Any? {
        val value = (model.assignments[term] as? ConstIntTerm)?.value ?: return null
        return memoryMappings.getOrPut(value) {
            null
        }
    }
}