package org.jetbrains.research.kex.smt.model

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.DoubleType
import org.jetbrains.research.kfg.type.FloatType
import org.jetbrains.research.kfg.type.Integral
import java.lang.reflect.Array

private val Term.isPointer get() = this.type is KexPointer
private val Term.isPrimary get() = !this.isPointer

data class RecoveredModel(val method: Method, val instance: Any?, val arguments: List<Any?>)

@Deprecated("Use ObjectRecoverer instead")
class ModelRecoverer(val method: Method, val model: SMTModel, val loader: ClassLoader) {
    val tf = TermFactory

    private val randomizer by lazy { defaultRandomizer }

    private val memoryMappings = hashMapOf<Int, MutableMap<Int, Any?>>()

    private fun memory(memspace: Int, address: Int) =
            memoryMappings.getOrPut(memspace, ::hashMapOf)[address]

    private fun memory(memspace: Int, address: Int, getter: () -> Any?) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address, getter)

    private fun memory(memspace: Int, address: Int, value: Any?) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address) { value }

    fun apply(): RecoveredModel {
        val thisTerm = model.assignments.keys.firstOrNull { it.toString().startsWith("this") }
        val instance = thisTerm?.let { recoverTerm(it) }

        val modelArgs = model.assignments.keys.asSequence()
                .mapNotNull { it as? ArgumentTerm }.map { it.index to it }.toMap()

        val recoveredArgs = arrayListOf<Any?>()

        for (index in 0..method.argTypes.lastIndex) {
            val modelArg = modelArgs[index]
            val recoveredArg = modelArg?.let { recoverTerm(it) }

            recoveredArgs += recoveredArg ?: when {
                method.argTypes[index].isPrimary -> when (method.argTypes[index]) {
                    is Integral -> 0
                    is FloatType -> 0.0F
                    is DoubleType -> 0.0
                    else -> unreachable { log.error("Unknown primary type ${method.argTypes[index]}") }
                }
                else -> null
            }
        }

        return RecoveredModel(method, instance, recoveredArgs)
    }

    private fun recoverTerm(term: Term, value: Term? = model.assignments[term]): Any? = when {
        term.isPrimary -> recoverPrimary(term.type, value)
        else -> recoverReferenceTerm(term, value)
    }

    private fun recoverPrimary(type: KexType, value: Term?): Any? {
        if (value == null) return randomizer.next(getClass(type.getKfgType(method.cm.type), loader))
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

        return memory(type.memspace, address) {
            val kfgClass = method.cm.getByName(type.`class`)
            val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) } ?: return@memory null
            val instance = randomizer.nextOrNull(`class`)
            for ((_, field) in kfgClass.fields) {
                val fieldCopy = tf.getField(KexReference(field.type.kexType), term, tf.getString(field.name))
                val fieldTerm = model.assignments.keys.firstOrNull { it == fieldCopy } ?: continue

                val memspace = fieldTerm.memspace
                val fieldAddress = model.assignments[fieldTerm]
                val fieldValue = model.memories[memspace]!!.finalMemory[fieldAddress]

                val recoveredValue = recoverTerm(fieldTerm, fieldValue)

                val fieldReflect = `class`.getDeclaredField(field.name)
                fieldReflect.isAccessible = true
                fieldReflect.set(instance, recoveredValue)
            }
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

        val memspace = arrayType.memspace
        val instance = run {
            val bounds = model.bounds[memspace] ?: return@run null
            val bound = (bounds.finalMemory[value] as? ConstIntTerm)?.value ?: return@run null

            val elementSize = arrayType.element.bitsize
            val elements = bound / elementSize

            val elementClass = getClass(arrayType.element.getKfgType(method.cm.type), loader)
            log.debug("Creating array of type $elementClass with size $elements")
            val instance = Array.newInstance(elementClass, elements)

            val assignedElements = model.assignments.keys
                    .mapNotNull { it as? ArrayIndexTerm }
                    .filter { it.arrayRef == term }

            for (index in assignedElements) {
                val indexMemspace = index.memspace
                val indexAddress = model.assignments[index] as? ConstIntTerm
                        ?: unreachable { log.error("Non-int address") }

                val element = model.memories[indexMemspace]?.finalMemory!![indexAddress]

                val `object` = recoverTerm(index, element)
                val actualIndex = (indexAddress.value - address) / elementSize
                if (actualIndex < elements)
                    Array.set(instance, actualIndex, `object`)
            }
            instance
        }
        return memory(arrayType.memspace, address, instance)
    }
}

class ObjectRecoverer(val method: Method, val model: SMTModel, val loader: ClassLoader) {
    private val randomizer by lazy { defaultRandomizer }

    private val memoryMappings = hashMapOf<Int, MutableMap<Int, Any?>>()

    private fun memory(memspace: Int, address: Int) =
            memoryMappings.getOrPut(memspace, ::hashMapOf)[address]

    private fun memory(memspace: Int, address: Int, getter: () -> Any?) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address, getter)

    private fun memory(memspace: Int, address: Int, value: Any?) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address) { value }

    fun recoverTerm(term: Term, value: Term? = model.assignments[term]): Any? = when {
        term.isPrimary -> recoverPrimary(term.type, value)
        else -> recoverPointer(term, value)
    }

    private fun recoverPrimary(type: KexType, value: Term?): Any? {
        if (value == null) return randomizer.next(getClass(type.getKfgType(method.cm.type), loader))
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

    private fun recoverPointer(term: Term, addr: Term?): Any? = when (term.type) {
        is KexClass -> recoverClass(term, addr)
        is KexArray -> recoverArray(term, addr)
        is KexReference -> recoverReference(term, addr)
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun recoverClass(term: Term, addr: Term?): Any? {
        val type = term.type as KexClass
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        return memory(type.memspace, address) {
            val kfgClass = method.cm.getByName(type.`class`)
            val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) } ?: return@memory null
            val instance = randomizer.nextOrNull(`class`)
            instance
        }
    }

    private fun recoverArray(term: Term, addr: Term?): Any? {
        val arrayType = term.type as KexArray
        val address = (addr as? ConstIntTerm)?.value ?: return null

        val memspace = arrayType.memspace
        val instance = run {
            val bounds = model.bounds[memspace] ?: return@run null
            val bound = (bounds.finalMemory[addr] as? ConstIntTerm)?.value ?: return@run null

            val elementSize = arrayType.element.bitsize
            val elements = bound / elementSize

            val elementClass = getClass(arrayType.element.getKfgType(method.cm.type), loader)
            log.debug("Creating array of type $elementClass with size $elements")
            val instance = Array.newInstance(elementClass, elements)

            instance
        }
        return memory(arrayType.memspace, address, instance)
    }

    private fun recoverReference(term: Term, addr: Term?): Any? {
        val memspace = term.memspace
        val refValue = model.memories[memspace]?.finalMemory!![addr]
        return when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                val index = (term.index as? ConstIntTerm)?.value ?: (model.assignments[term.index] as ConstIntTerm).value

                val arrayAddr = (model.assignments[arrayRef] as ConstIntTerm).value
                val array = memory(arrayRef.memspace, arrayAddr) ?: return null

                val recoveredValue = recoverReferenceValue(term, refValue)
                Array.set(array, index, recoveredValue)
                return array
            }
            is FieldTerm -> {
                // TODO: support static fields
                val objectRef = if (!term.isStatic) term.owner else return null
                val objectAddr = (model.assignments[objectRef] as ConstIntTerm).value
                val type = objectRef.type as KexClass

                val kfgClass = method.cm.getByName(type.`class`)
                val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) } ?: return null
                val instance = memory(objectRef.memspace, objectAddr) ?: return null

                val fieldAddress = model.assignments[term]
                val fieldValue = model.memories[memspace]!!.finalMemory[fieldAddress]

                val recoveredValue = recoverTerm(term, fieldValue)
                val fieldReflect = `class`.getDeclaredField((term.fieldName as ConstStringTerm).value)
                fieldReflect.isAccessible = true
                fieldReflect.set(instance, recoveredValue)
                return instance
            }
            else -> unreachable { log.error("Unknown reference term: $term with address $addr") }
        }
    }

    private fun recoverReferenceValue(term: Term, value: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        if (value == null) return null
        val intVal = (value as ConstIntTerm).value

        return when (referencedType) {
            is KexPointer -> {
                // TODO: support pointer references
                null
            }
            is KexBool -> intVal.toBoolean()
            is KexByte -> intVal.toByte()
            is KexChar -> intVal.toChar()
            is KexShort -> intVal.toShort()
            is KexInt -> intVal
            is KexLong -> intVal.toLong()
            is KexFloat -> intVal.toFloat()
            is KexDouble -> intVal.toDouble()
            else -> unreachable { log.error("Can't recover type $referencedType from memory value $value") }
        }
    }
}