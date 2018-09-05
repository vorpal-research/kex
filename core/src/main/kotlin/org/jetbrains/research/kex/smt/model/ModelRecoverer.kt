package org.jetbrains.research.kex.smt.model

import org.jetbrains.research.kex.driver.RandomDriver
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.toBoolean
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method


private fun Term.isPointer() = this.type is KexPointer
private fun Term.isPrimary() = !isPointer()

class ModelRecoverer(val method: Method, val model: SMTModel, val loader: ClassLoader) {
    val tf = TermFactory
    val terms = hashMapOf<Term, Any?>()

    val memoryMappings = hashMapOf<Int, Any?>(0 to null)

    fun apply() {
        val recoveringTerms = hashSetOf<Term>()
        recoveringTerms += method.desc.args.withIndex().map { (index, type) -> tf.getArgument(type.kexType, index) }

        if (!method.isAbstract) {
            val `this` = tf.getThis(method.`class`)
            recoveringTerms += `this`
        }

        for (term in recoveringTerms) {
            terms[term] = recoverTerm(term)
        }
    }

    private fun recoverTerm(term: Term, value: Term? = model.assignments[term]): Any? = when {
        term.isPrimary() -> recoverPrimary(term.type, value)
        else -> recoverReferenceTerm(term, value)
    }

    private fun recoverPrimary(type: KexType, value: Term?): Any? {
        if (value == null) return null
        return when (type) {
            is KexBool -> (value as ConstBoolTerm).value
            is KexByte -> (value as ConstByteTerm).value
            is KexChar -> (value as ConstCharTerm).value
            is KexShort -> (value as ConstShortTerm).value
            is KexInt -> (value as ConstIntTerm).value
            is KexLong -> (value as ConstLongTerm).value
            is KexFloat -> (value as ConstFloatTerm).value
            is KexDouble -> (value as ConstDoubleTerm).value
            else -> unreachable { log.error("Trying to recover non-primary term as primary value: $value with type $type") }
        }
    }

    private fun recoverReferenceTerm(term: Term, value: Term?): Any? = when (term.type) {
        is KexClass -> recoverClass(term, value)
        is KexArray -> recoverArray(term, value)
        is KexReference -> recoverReference(term, value)
        else -> unreachable { log.error("Trying to recover non-reference term $term with type ${term.type} as reference value") }
    }

    private fun recoverClass(term: Term, value: Term?): Any? {
        val type = term.type as KexClass
        val address = (value as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        return memoryMappings.getOrPut(address) {
            val `class` = loader.loadClass(type.`class`.canonicalDesc)
            val instance = RandomDriver.generateOrNull(`class`) ?: return null
            for ((_, field) in type.`class`.fields) {
                val fieldCopy = tf.getField(KexReference(field.type.kexType), term, tf.getString(field.name))
                val fieldTerm = model.assignments.keys.firstOrNull { it == fieldCopy } ?: continue

                val memspace = fieldTerm.memspace
                val fieldAddress = model.assignments[fieldTerm]
                val fieldValue = model.memories[memspace]!!.finalMemory[fieldAddress]

                val recoveredValue = recoverTerm(fieldTerm, fieldValue)

                log.debug("Field ${field.name} have address $fieldAddress and value $fieldValue and recovered $recoveredValue")

                val fieldReflect = `class`.getDeclaredField(field.name)
                fieldReflect.isAccessible = true
                fieldReflect.set(instance, recoveredValue)
            }
            log.debug("Generated for type $type: $instance")
            instance
        }
    }

    private fun recoverReference(term: Term, value: Term?): Any? {
        val referenced = (term.type as KexReference).reference
        if (value == null) return null
        val intVal = (value as ConstIntTerm).value

        return when (referenced) {
            is KexPointer -> null
            is KexBool -> intVal.toBoolean()
            is KexByte -> intVal.toByte()
            is KexChar -> intVal.toChar()
            is KexShort -> intVal.toShort()
            is KexInt -> intVal
            is KexLong -> intVal.toLong()
            is KexFloat -> intVal.toFloat()
            is KexDouble -> intVal.toDouble()
            else -> unreachable { log.error("Can't recover type $referenced from memory value $value") }
        }
    }

    private fun recoverArray(term: Term, value: Term?): Any? {
        val arrayType = term.type as KexArray
        val address = (value as? ConstIntTerm)?.value ?: return null
        return memoryMappings.getOrPut(address) {
            null
        }
    }
}