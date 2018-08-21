package org.jetbrains.research.kex.smt.model

import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*


private fun Term.isReference() = this.type.isReference
private fun Term.isPrimary() = !isReference()

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
            terms[term] = recoverTerm(term)
        }
    }

    private fun recoverTerm(term: Term, value: Term? = model.assignments[term]): Any? = when {
        term.isPrimary() -> recoverPrimaryTerm(term, value)
        else -> recoverReferenceTerm(term, value)
    }

    private fun recoverPrimaryTerm(term: Term, value: Term?): Any? {
        if (value == null) return null
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

    private fun recoverReferenceTerm(term: Term, value: Term?): Any? = when (term.type) {
        is ClassType -> recoverClassTerm(term, value)
        is ArrayType -> recoverArrayTerm(term, value)
        else -> unreachable { log.error("Trying to recover non-reference term $term with type ${term.type} as reference value") }
    }

    private fun recoverClassTerm(term: Term, value: Term?): Any? {
        val type = term.type as ClassType
        val address = (value as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        return memoryMappings.getOrPut(address) {
            val `class` = loader.loadClass(type.`class`.canonicalDesc)
            val instance = RandomDriver.generateOrNull(`class`) ?: return null
            for ((_, field) in type.`class`.fields) {
                val fieldTerm = model.assignments.keys.firstOrNull { it == tf.getField(field.type, term, tf.getString(field.name)) }
                        ?: continue

                val memspace = fieldTerm.memspace
                val fieldAddress = model.assignments[fieldTerm]
                val fieldValue = model.memories[memspace]!!.finalMemory[fieldAddress]

                val recoveredValue = recoverTerm(fieldTerm, fieldValue)

                log.debug("Field $field have address $fieldAddress and value $fieldValue and recovered $recoveredValue")

                val fieldReflect = `class`.getDeclaredField(field.name)
                fieldReflect.isAccessible = true
                fieldReflect.set(instance, recoveredValue)
            }
            log.debug("Generated for type $type: $instance")
            instance
        }
    }

    private fun recoverArrayTerm(term: Term, value: Term?): Any? {
        val arrayType = term.type as ArrayType
        val address = (value as? ConstIntTerm)?.value ?: return null
        return memoryMappings.getOrPut(address) {
            null
        }
    }
}