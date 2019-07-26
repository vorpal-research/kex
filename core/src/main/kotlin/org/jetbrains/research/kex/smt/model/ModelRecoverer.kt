package org.jetbrains.research.kex.smt.model

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.DoubleType
import org.jetbrains.research.kfg.type.FloatType
import org.jetbrains.research.kfg.type.Integral
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type

private val Term.isPointer get() = this.type is KexPointer
private val Term.isPrimary get() = !this.isPointer

private var Field.isFinal: Boolean
    get() = (this.modifiers and Modifier.FINAL) == Modifier.FINAL
    set(value) {
        if (value == this.isFinal) return
        val modifiersField = this.javaClass.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(this, this.modifiers and if (value) Modifier.FINAL else Modifier.FINAL.inv())
    }

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
        if (value == null) return randomizer.next(loader.loadClass(type.getKfgType(method.cm.type)))
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

            val elementClass = loader.loadClass(arrayType.element.getKfgType(method.cm.type))
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

class ObjectRecoverer(val method: Method,
                      val model: SMTModel,
                      val loader: ClassLoader,
                      val randomizer: Randomizer) {
    private val memoryMappings = hashMapOf<Int, MutableMap<Int, Any?>>()

    private fun memory(memspace: Int, address: Int) =
            memoryMappings.getOrPut(memspace, ::hashMapOf)[address]

    private fun memory(memspace: Int, address: Int, getter: () -> Any?) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address, getter)

    private fun memory(memspace: Int, address: Int, value: Any?) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address) { value }

    fun recoverTerm(term: Term,
                    jType: Type = loader.loadClass(term.type.getKfgType(method.cm.type)),
                    value: Term? = model.assignments[term]): Any? = when {
        term.isPrimary -> recoverPrimary(term.type, jType, value)
        else -> recoverPointer(term, jType, value)
    }

    private fun recoverPrimary(type: KexType, jType: Type, value: Term?): Any? {
        if (value == null) return randomizer.next(jType)
        return when (type) {
            is KexBool -> (value as ConstBoolTerm).value
            is KexByte -> (value as ConstByteTerm).value
            is KexChar -> (value as ConstIntTerm).value.toChar()
            is KexShort -> (value as ConstShortTerm).value
            is KexInt -> (value as ConstIntTerm).value
            is KexLong -> (value as ConstLongTerm).value
            is KexFloat -> (value as ConstFloatTerm).value
            is KexDouble -> (value as ConstDoubleTerm).value
            else -> unreachable { log.error("Trying to recover non-primary term as primary value: $value with type $type") }
        }
    }

    private fun recoverPointer(term: Term, jType: Type, addr: Term?): Any? = when (term.type) {
        is KexClass -> recoverClass(term, jType, addr)
        is KexArray -> recoverArray(term, jType, addr)
        is KexReference -> recoverReference(term, jType, addr)
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun recoverClass(term: Term, jType: Type, addr: Term?): Any? {
        val type = term.type as KexClass
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        return memory(type.memspace, address) { randomizer.nextOrNull(jType) }
    }

    private fun recoverArray(term: Term, jType: Type, addr: Term?): Any? {
        val arrayType = term.type as KexArray
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val memspace = arrayType.memspace
        val instance = run {
            // if model does not contain any information about bounds of current array, we can create array of any length
            val bound = (model.bounds[memspace]?.finalMemory?.get(addr) as? ConstIntTerm)?.value ?: 0
            val elementSize = arrayType.element.bitsize
            val elements = bound / elementSize

            val elementClass = (jType as? Class<*>)?.componentType ?: unreachable { log.error("Undefined jType in array recovery: $jType") }
            log.debug("Creating array of type $elementClass with size $elements")
            val instance = Array.newInstance(elementClass, elements)

            instance
        }
        return memory(arrayType.memspace, address, instance)
    }

    private fun recoverReference(term: Term, jType: Type, addr: Term?): Any? {
        val memspace = term.memspace
        val refValue = model.memories[memspace]?.finalMemory!![addr]
        return when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                val elementType = (arrayRef.type as KexArray).element

                val arrayAddr = (model.assignments[arrayRef] as ConstIntTerm).value
                val array = memory(arrayRef.memspace, arrayAddr) ?: return null

                val recoveredValue = recoverReferenceValue(term, jType, refValue)
                val address = (addr as? ConstIntTerm)?.value ?: unreachable { log.error("Non-int address of array index") }
                val realIndex = (address - arrayAddr) / elementType.bitsize
                Array.set(array, realIndex, recoveredValue)
                array
            }
            is FieldTerm -> {
                val (instance, klass) = when {
                    term.isStatic -> {
                        val classRef = (term.owner as ConstClassTerm)
                        val `class` = tryOrNull { loader.loadClass(classRef.`class`.canonicalDesc) } ?: return null
                        if (`class`.isSynthetic) return null
                        null to `class`
                    }
                    else -> {
                        val objectRef = term.owner
                        val objectAddr = (model.assignments[objectRef] as ConstIntTerm).value
                        val type = objectRef.type as KexClass

                        val kfgClass = method.cm.getByName(type.`class`)
                        val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) } ?: return null
                        val instance = memory(objectRef.memspace, objectAddr) ?: return null
                        instance to `class`
                    }
                }
                val fieldAddress = model.assignments[term]
                val fieldValue = model.memories.getValue(memspace).finalMemory[fieldAddress]

                val fieldReflect = klass.getDeclaredField((term.fieldName as ConstStringTerm).value)
                val recoveredValue = recoverReferenceValue(term, fieldReflect.genericType, fieldValue)
                fieldReflect.isAccessible = true
                fieldReflect.isFinal = false
                if (fieldReflect.isEnumConstant || fieldReflect.isSynthetic) return instance
                if (fieldReflect.type.isPrimitive) {
                    val definedValue = recoveredValue ?: recoverPrimary((term.type as KexReference).reference, fieldReflect.type, null)!!
                    when (definedValue.javaClass) {
                        Boolean::class.javaObjectType -> fieldReflect.setBoolean(instance, definedValue as Boolean)
                        Byte::class.javaObjectType -> fieldReflect.setByte(instance, definedValue as Byte)
                        Char::class.javaObjectType -> fieldReflect.setChar(instance, definedValue as Char)
                        Short::class.javaObjectType -> fieldReflect.setShort(instance, definedValue as Short)
                        Int::class.javaObjectType -> fieldReflect.setInt(instance, definedValue as Int)
                        Long::class.javaObjectType -> fieldReflect.setLong(instance, definedValue as Long)
                        Float::class.javaObjectType -> fieldReflect.setFloat(instance, definedValue as Float)
                        Double::class.javaObjectType -> fieldReflect.setDouble(instance, definedValue as Double)
                        else -> unreachable { log.error("Trying to get primitive type of non-primitive object $this") }
                    }
                } else {
                    fieldReflect.set(instance, recoveredValue)
                }
                instance
            }
            else -> unreachable { log.error("Unknown reference term: $term with address $addr") }
        }
    }

    private fun recoverReferenceValue(term: Term, jType: Type, value: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        if (value == null) return null
        val intVal = (value as ConstIntTerm).value

        return when (referencedType) {
            is KexPointer -> recoverReferencePointer(term, jType, value)
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

    private fun recoverReferencePointer(term: Term, jType: Type, addr: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null
        return when (referencedType) {
            is KexClass -> memory(referencedType.memspace, address) { randomizer.nextOrNull(jType) }
            is KexArray -> {
                val memspace = referencedType.memspace
                val instance = run {
                    val bounds = model.bounds[memspace] ?: return@run null
                    val bound = (bounds.finalMemory[addr] as? ConstIntTerm)?.value ?: return@run null

                    val elementSize = referencedType.element.bitsize
                    val elements = bound / elementSize

                    val elementClass = jType as Class<*>
                    log.debug("Creating array of type $elementClass with size $elements")
                    val instance = Array.newInstance(elementClass, elements)

                    instance
                }
                memory(referencedType.memspace, address, instance)
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }
}