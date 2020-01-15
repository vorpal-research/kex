package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ir.Method
import java.lang.reflect.*
import java.lang.reflect.Array

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

data class ReanimatedModel(val method: Method, val instance: Any?, val arguments: List<Any?>)

class ObjectReanimator(val method: Method,
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

    fun reanimateTerm(term: Term,
                      jType: Type = loader.loadClass(term.type.getKfgType(method.cm.type)),
                      value: Term? = model.assignments[term]): Any? = when {
        term.isPrimary -> reanimatePrimary(term.type, jType, value)
        else -> reanimatePointer(term, jType, value)
    }

    private fun reanimatePrimary(type: KexType, jType: Type, value: Term?): Any? {
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

    private fun reanimatePointer(term: Term, jType: Type, addr: Term?): Any? = when (term.type) {
        is KexClass -> reanimateClass(term, jType, addr)
        is KexArray -> reanimateArray(term, jType, addr)
        is KexReference -> reanimateReference(term, jType, addr)
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun reanimateClass(term: Term, jType: Type, addr: Term?): Any? {
        val type = term.type as KexClass
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        return memory(type.memspace, address) { randomizer.nextOrNull(jType) }
    }

    private fun reanimateArray(term: Term, jType: Type, addr: Term?): Any? {
        val arrayType = term.type as KexArray
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val memspace = arrayType.memspace
        val instance = run {
            // if model does not contain any information about bounds of current array, we can create array of any length
            val bound = (model.bounds[memspace]?.finalMemory?.get(addr) as? ConstIntTerm)?.value ?: 0
            val elementSize = arrayType.element.bitsize
            val elements = bound / elementSize

            val elementType = when (jType) {
                is Class<*> -> jType.componentType
                is GenericArrayType -> randomizer.nextOrNull(jType.genericComponentType)?.javaClass
                else -> unreachable { log.error("Unknown jType in array recovery: $jType") }
            }
            log.debug("Creating array of type $elementType with size $elements")
            val instance = Array.newInstance(elementType, elements)

            instance
        }
        return memory(arrayType.memspace, address, instance)
    }

    private fun reanimateReference(term: Term, jType: Type, addr: Term?): Any? {
        val memspace = term.memspace
        val refValue = model.memories[memspace]?.finalMemory!![addr]
        return when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                val elementType = (arrayRef.type as KexArray).element

                val arrayAddr = (model.assignments[arrayRef] as ConstIntTerm).value
                val array = memory(arrayRef.memspace, arrayAddr) ?: return null

                val reanimatedValue = reanimateReferenceValue(term, jType, refValue)
                val address = (addr as? ConstIntTerm)?.value ?: unreachable { log.error("Non-int address of array index") }
                val realIndex = (address - arrayAddr) / elementType.bitsize
                Array.set(array, realIndex, reanimatedValue)
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

                val fieldReflect = klass.getActualField((term.fieldName as ConstStringTerm).value)
                if (!klass.isAssignableFrom(instance?.javaClass ?: Any::class.java)) {
                    log.warn("Could not generate an instance of $klass, so skipping filed initialization")
                    return instance
                }
                val reanimatedValue = reanimateReferenceValue(term, fieldReflect.genericType, fieldValue)
                fieldReflect.isAccessible = true
                fieldReflect.isFinal = false
                if (fieldReflect.isEnumConstant || fieldReflect.isSynthetic) return instance
                if (fieldReflect.type.isPrimitive) {
                    val definedValue = reanimatedValue ?: reanimatePrimary((term.type as KexReference).reference, fieldReflect.type, null)!!
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
                    fieldReflect.set(instance, reanimatedValue)
                }
                instance
            }
            else -> unreachable { log.error("Unknown reference term: $term with address $addr") }
        }
    }

    private fun reanimateReferenceValue(term: Term, jType: Type, value: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        if (value == null) return null
        val intVal = (value as ConstIntTerm).value

        return when (referencedType) {
            is KexPointer -> reanimateReferencePointer(term, jType, value)
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

    private fun reanimateReferencePointer(term: Term, jType: Type, addr: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null
        return when (referencedType) {
            is KexClass -> memory(term.memspace, address) { randomizer.nextOrNull(jType) }
            is KexArray -> {
                val memspace = term.memspace//referencedType.memspace
                val instance = run {
                    val bounds = model.bounds[memspace] ?: return@run null
                    val bound = (bounds.finalMemory[addr] as? ConstIntTerm)?.value ?: return@run null

                    val elementSize = referencedType.element.bitsize
                    val elements = bound / elementSize

                    val elementType = when (jType) {
                        is Class<*> -> jType.componentType
                        is GenericArrayType -> randomizer.nextOrNull(jType.genericComponentType)?.javaClass
                        else -> unreachable { log.error("Unknown jType in array recovery: $jType") }
                    }
                    log.debug("Creating array of type $elementType with size $elements")
                    val instance = Array.newInstance(elementType, elements)

                    instance
                }
                memory(memspace, address, instance)
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }
}