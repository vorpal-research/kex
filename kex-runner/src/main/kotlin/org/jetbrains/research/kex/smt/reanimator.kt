package org.jetbrains.research.kex.smt

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import com.abdullin.kthelper.toBoolean
import com.abdullin.kthelper.tryOrNull
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.generator.ArrayDescriptor
import org.jetbrains.research.kex.generator.Descriptor
import org.jetbrains.research.kex.generator.ObjectDescriptor
import org.jetbrains.research.kex.generator.descriptor
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.getActualField
import org.jetbrains.research.kex.util.loadClass
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

private val Type.simpleTypeName: String
    get() = when (this) {
        is ParameterizedType -> this.rawType.simpleTypeName
        else -> this.typeName
    }

data class ReanimatedModel(val method: Method, val instance: Any?, val arguments: List<Any?>)

interface Reanimator<T> {
    val method: Method
    val model: SMTModel
    val context: ExecutionContext

    val loader: ClassLoader get() = context.loader

    val memoryMappings: MutableMap<Int, MutableMap<Int, T>>

    fun memory(memspace: Int, address: Int) =
            memoryMappings.getOrPut(memspace, ::hashMapOf)[address]

    fun memory(memspace: Int, address: Int, getter: () -> T) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address, getter)

    fun memory(memspace: Int, address: Int, value: T) =
            memoryMappings.getOrPut(memspace, ::hashMapOf).getOrPut(address) { value }

    fun reanimate(term: Term,
                  value: Term? = model.assignments[term]): T

    fun reanimateFromAssignment(term: Term): Term?
    fun reanimateFromMemory(memspace: Int, addr: Term?): Term?
    fun reanimateFromProperties(memspace: Int, name: String, addr: Term?): Term?

    fun reanimateType(memspace: Int, addr: Term?): KexType? {
        val typeVar = reanimateFromProperties(memspace, "type", addr) ?: return null
        return model.typeMap[typeVar]
    }
}

class ObjectReanimator(override val method: Method,
                       override val model: SMTModel,
                       override val context: ExecutionContext) : Reanimator<Any?> {
    private val randomizer get() = context.random

    override val memoryMappings = hashMapOf<Int, MutableMap<Int, Any?>>()

    override fun reanimate(term: Term, value: Term?): Any? = when {
        term.isPrimary -> reanimatePrimary(term.type, value)
        else -> reanimatePointer(term, value)
    }

    override fun reanimateFromAssignment(term: Term) = model.assignments[term]
    override fun reanimateFromMemory(memspace: Int, addr: Term?) = model.memories[memspace]?.finalMemory?.get(addr)
    override fun reanimateFromProperties(memspace: Int, name: String, addr: Term?) =
            model.properties[memspace]?.get(name)?.finalMemory?.get(addr)

    private fun reanimatePrimary(type: KexType, value: Term?): Any? {
        if (value == null) return randomizer.next(loader.loadClass(context.types, type))
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

    private fun reanimatePointer(term: Term, addr: Term?): Any? = when (term.type) {
        is KexClass -> reanimateClass(term, addr)
        is KexArray -> reanimateArray(term, addr)
        is KexReference -> reanimateReference(term, addr)
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun reanimateClass(term: Term, addr: Term?): Any? {
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val type = reanimateType(term.memspace, addr) ?: term.type
        return memory(term.memspace, address) { randomizer.nextOrNull(loader.loadClass(context.types, type)) }
    }

    private fun reanimateArray(term: Term, addr: Term?): Any? {
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val instance = newArrayInstance(term.type as KexArray, addr)
        return memory(term.memspace, address, instance)
    }

    private fun reanimateReference(term: Term, addr: Term?): Any? {
        val memspace = term.memspace
        val refValue = reanimateFromMemory(memspace, addr)
        return when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                val elementType = (arrayRef.type as KexArray).element

                val arrayAddr = (reanimateFromAssignment(arrayRef) as ConstIntTerm).value
                val array = memory(arrayRef.memspace, arrayAddr) ?: return null

                val reanimatedValue = reanimateReferenceValue(term, refValue)
                val address = (addr as? ConstIntTerm)?.value
                        ?: unreachable { log.error("Non-int address of array index") }
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
                        val objectAddr = (reanimateFromAssignment(objectRef) as ConstIntTerm).value
                        val type = objectRef.type as KexClass

                        val kfgClass = method.cm[type.`class`]
                        val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) } ?: return null
                        val instance = memory(objectRef.memspace, objectAddr) ?: return null
                        instance to `class`
                    }
                }
                val name = "${term.klass}.${term.fieldNameString}"
                val fieldValue = reanimateFromProperties(memspace, name, addr)

                val fieldReflect = klass.getActualField(term.fieldNameString)
                if (!klass.isAssignableFrom(instance?.javaClass ?: Any::class.java)) {
                    log.warn("Could not generate an instance of $klass, so skipping filed initialization")
                    return instance
                }
                val reanimatedValue = reanimateReferenceValue(term, fieldValue)
                fieldReflect.isAccessible = true
                fieldReflect.isFinal = false
                if (fieldReflect.isEnumConstant || fieldReflect.isSynthetic) return instance
                if (fieldReflect.type.isPrimitive) {
                    val definedValue = reanimatedValue
                            ?: reanimatePrimary((term.type as KexReference).reference, null)!!
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

    private fun reanimateReferenceValue(term: Term, value: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        if (value == null) return null

        return when (value) {
            is ConstDoubleTerm -> value.value
            is ConstFloatTerm -> value.value
            else -> {
                val intVal = (value as ConstIntTerm).value
                when (referencedType) {
                    is KexPointer -> reanimateReferencePointer(term, value)
                    is KexBool -> intVal.toBoolean()
                    is KexLong -> intVal.toLong()
                    is KexFloat -> intVal.toFloat()
                    is KexDouble -> intVal.toDouble()
                    else -> intVal
                }
            }
        }
    }

    private fun reanimateReferencePointer(term: Term, addr: Term?): Any? {
        val referencedType = (term.type as KexReference).reference
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val type = reanimateType(term.memspace, addr) ?: term.type
        return when (referencedType) {
            is KexClass -> memory(term.memspace, address) { randomizer.nextOrNull(loader.loadClass(context.types, type)) }
            is KexArray -> {
                val memspace = term.memspace//referencedType.memspace
                val instance = newArrayInstance(referencedType, addr)
                memory(memspace, address, instance)
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }

    private fun newArrayInstance(type: KexArray, addr: Term?): Any? {
        val length = (reanimateFromProperties(type.memspace, "length", addr) as? ConstIntTerm)?.value ?: return null

        val actualType = (reanimateType(type.memspace, addr) ?: type) as? KexArray
                ?: unreachable { log.error("Non-array type in reanimate array") }
        val elementType = loader.loadClass(context.types, actualType.element)
        log.debug("Creating array of type $elementType with size $length")
        return Array.newInstance(elementType, length)
    }
}

abstract class DescriptorReanimator(override val method: Method,
                                    override val model: SMTModel,
                                    override val context: ExecutionContext) : Reanimator<Descriptor> {
    private val types get() = context.types
    override val memoryMappings = hashMapOf<Int, MutableMap<Int, Descriptor>>()

    override fun reanimate(term: Term, value: Term?): Descriptor = when {
        term.isPrimary -> reanimatePrimary(term, value)
        else -> reanimatePointer(term, value)
    }

    private fun reanimatePrimary(term: Term, value: Term?) = descriptor(context) {
        if (value == null) default(term.type, false)
        else when (term.type) {
            is KexBool -> const((value as ConstBoolTerm).value)
            is KexByte -> const((value as ConstByteTerm).value)
            is KexChar -> const((value as ConstIntTerm).value)
            is KexShort -> const((value as ConstShortTerm).value)
            is KexInt -> const((value as ConstIntTerm).value)
            is KexLong -> const((value as ConstLongTerm).value)
            is KexFloat -> const((value as ConstFloatTerm).value)
            is KexDouble -> const((value as ConstDoubleTerm).value)
            else -> unreachable { log.error("Trying to recover non-primary term as primary value: $value with type ${term.type}") }
        }
    }

    private fun reanimatePointer(term: Term, addr: Term?) = when (term.type) {
        is KexClass -> reanimateClass(term, addr)
        is KexArray -> reanimateArray(term, addr)
        is KexReference -> reanimateReference(term, addr)
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun reanimateClass(term: Term, addr: Term?) = descriptor(context) {
        val actualType = (reanimateType(term.memspace, addr) ?: term.type) as KexClass

        when (val address = (addr as? ConstIntTerm)?.value) {
            null, 0 -> default(actualType)
            else -> memory(term.memspace, address) { `object`(context.cm[actualType.`class`]) }
        }
    }

    private fun reanimateArray(term: Term, addr: Term?) = descriptor(context) {
        val arrayType = (reanimateType(term.memspace, addr) ?: term.type) as KexArray

        when (val address = (addr as? ConstIntTerm)?.value) {
            null, 0 -> default(arrayType)
            else -> memory(term.memspace, address) {
                newArrayInstance(term.memspace, arrayType, addr)
            }
        }
    }

    private fun reanimateReference(term: Term, addr: Term?) = descriptor(context) {
        val memspace = term.memspace
        val refValue = reanimateFromMemory(memspace, addr)
        when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                val elementType = (arrayRef.type as KexArray).element

                val arrayAddr = (reanimateFromAssignment(arrayRef) as ConstIntTerm).value
                val array = memory(arrayRef.memspace, arrayAddr) as? ArrayDescriptor
                        ?: return@descriptor default(term.type)

                val reanimatedValue = reanimateReferenceValue(term, refValue)
                val address = (addr as? ConstIntTerm)?.value
                        ?: unreachable { log.error("Non-int address of array index") }
                val realIndex = (address - arrayAddr) / elementType.bitsize
                array[realIndex] = reanimatedValue
                array
            }
            is FieldTerm -> {
                val fieldName = (term.fieldName as ConstStringTerm).value
                val (instance, klass, field) = when {
                    term.isStatic -> {
                        val classRef = (term.owner as ConstClassTerm)
                        val `class` = tryOrNull { loader.loadClass(classRef.`class`.canonicalDesc) }
                                ?: return@descriptor default(term.type)
                        if (`class`.isSynthetic) return@descriptor default(term.type)

                        Triple(`null`, `class`, classRef.`class`.getField(fieldName, term.type.getKfgType(types)))
                    }
                    else -> {
                        val objectRef = term.owner
                        val objectAddr = (reanimateFromAssignment(objectRef) as ConstIntTerm).value
                        val type = objectRef.type as KexClass

                        val kfgClass = method.cm[type.`class`]
                        val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) }
                                ?: return@descriptor default(term.type)

                        val instance = memory(objectRef.memspace, objectAddr)
                                ?: return@descriptor default(term.type)

                        Triple(instance, `class`, kfgClass.getField(fieldName, term.type.getKfgType(types)))
                    }
                }
                val name = "${term.klass}.${term.fieldNameString}"
                val fieldValue = reanimateFromProperties(memspace, name, addr)

                val fieldReflect = klass.getActualField(term.fieldNameString)
                val reanimatedValue = reanimateReferenceValue(term, fieldValue)
                if (fieldReflect.isEnumConstant || fieldReflect.isSynthetic)
                    return@descriptor default(term.type)

                if (instance is ObjectDescriptor) {
                    instance[fieldReflect.name] = instance.field(field.name, field.type, field.`class`, reanimatedValue)
                }

                instance
            }
            else -> unreachable { log.error("Unknown reference term: $term with address $addr") }
        }
    }

    private fun reanimateReferenceValue(term: Term, value: Term?) = descriptor(context) {
        val referencedType = (term.type as KexReference).reference
        if (value == null) return@descriptor default(term.type)

        when (value) {
            is ConstDoubleTerm -> const(value.value)
            is ConstFloatTerm -> const(value.value)
            else -> {
                val intVal = (value as ConstIntTerm).value
                when (referencedType) {
                    is KexPointer -> reanimateReferencePointer(term, value)
                    is KexBool -> const(intVal.toBoolean())
                    is KexLong -> const(intVal.toLong())
                    is KexFloat -> const(intVal.toFloat())
                    is KexDouble -> const(intVal.toDouble())
                    else -> const(intVal)
                }
            }
        }
    }

    private fun reanimateReferencePointer(term: Term, addr: Term?) = descriptor(context) {
        val referencedType = (term.type as KexReference).reference
        val address = (addr as? ConstIntTerm)?.value ?: return@descriptor default(term.type)
        if (address == 0) return@descriptor default(term.type)

        when (val actualType = (reanimateType(term.memspace, addr) ?: referencedType)) {
            is KexClass -> {
                memory(term.memspace, address) { `object`(actualType.kfgClass(context.types)) }
            }
            is KexArray -> {
                memory(term.memspace, address) {
                    newArrayInstance(term.memspace, actualType, addr)
                }
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }

    private fun newArrayInstance(memspace: Int, arrayType: KexArray, addr: Term?) = descriptor(context) {
        val length = (reanimateFromProperties(memspace, "length", addr) as? ConstIntTerm)?.value
                ?: return@descriptor default(arrayType)

        log.debug("Creating array of type $arrayType with size $length")
        array(length, arrayType.getKfgType(types))
    }
}

class FinalDescriptorReanimator(method: Method, model: SMTModel, context: ExecutionContext) :
        DescriptorReanimator(method, model, context) {
    override fun reanimateFromAssignment(term: Term) = model.assignments[term]
    override fun reanimateFromMemory(memspace: Int, addr: Term?) = model.memories[memspace]?.finalMemory?.get(addr)
    override fun reanimateFromProperties(memspace: Int, name: String, addr: Term?) =
            model.properties[memspace]?.get(name)?.finalMemory?.get(addr)
}

class InitialDescriptorReanimator(method: Method, model: SMTModel, context: ExecutionContext) :
        DescriptorReanimator(method, model, context) {
    override fun reanimateFromAssignment(term: Term) = model.assignments[term]
    override fun reanimateFromMemory(memspace: Int, addr: Term?) = model.memories[memspace]?.initialMemory?.get(addr)
    override fun reanimateFromProperties(memspace: Int, name: String, addr: Term?) =
            model.properties[memspace]?.get(name)?.initialMemory?.get(addr)
}
