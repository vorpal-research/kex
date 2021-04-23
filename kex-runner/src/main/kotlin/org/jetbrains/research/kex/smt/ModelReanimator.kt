package org.jetbrains.research.kex.smt

import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.toBoolean
import org.jetbrains.research.kthelper.tryOrNull
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.reanimator.descriptor.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kex.state.transformer.memspace
import org.jetbrains.research.kex.util.getActualField
import org.jetbrains.research.kex.util.isFinal
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ir.Method
import java.lang.reflect.Array

private val Term.isPointer get() = this.type is KexPointer
private val Term.isPrimary get() = !this.isPointer

interface ModelReanimator<T> {
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

    fun resolveType(memspace: Int, addr: Term?, default: KexType): KexType {
        val resolvedType = reanimateType(memspace, addr) ?: return default
        val resolvedKfg = resolvedType.getKfgType(context.types)
        val thisKfg = default.getKfgType(context.types)
        return when {
            !thisKfg.isConcrete -> default
            resolvedKfg.isSubtypeOf(thisKfg) -> resolvedType
            else -> default
        }
    }
}

class ObjectReanimator(override val method: Method,
                       override val model: SMTModel,
                       override val context: ExecutionContext) : ModelReanimator<Any?> {
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
            is KexByte -> value.numericValue.toByte()
            is KexChar -> value.numericValue.toChar()
            is KexShort -> value.numericValue.toShort()
            is KexInt -> value.numericValue.toInt()
            is KexLong -> value.numericValue.toLong()
            is KexFloat -> value.numericValue.toFloat()
            is KexDouble -> value.numericValue.toDouble()
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

        val type = resolveType(term.memspace, addr, term.type)//reanimateType(term.memspace, addr) ?: term.type
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
                val realIndex = (address - arrayAddr) / (elementType.bitsize / KexType.WORD)
                Array.set(array, realIndex, reanimatedValue)
                array
            }
            is FieldTerm -> {
                val (instance, klass) = when {
                    term.isStatic -> {
                        val canonicalDesc = term.type.getKfgType(context.types).canonicalDesc
                        val `class` = tryOrNull { loader.loadClass(canonicalDesc) } ?: return null
                        if (`class`.isSynthetic) return null
                        null to `class`
                    }
                    else -> {
                        val objectRef = term.owner
                        val objectAddr = (reanimateFromAssignment(objectRef) as ConstIntTerm).value
                        val type = objectRef.type as? KexClass
                                ?: unreachable { log.error("Cannot cast ${objectRef.type} to class") }

                        val kfgClass = method.cm[type.klass]
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

        val type = resolveType(term.memspace, addr, term.type)//reanimateType(term.memspace, addr) ?: term.type
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

        val actualType = resolveType(type.memspace, addr, type) as? KexArray//(reanimateType(type.memspace, addr) ?: type) as? KexArray
                ?: unreachable { log.error("Non-array type in reanimate array") }
        val elementType = loader.loadClass(context.types, actualType.element)
        log.debug("Creating array of type $elementType with size $length")
        return Array.newInstance(elementType, length)
    }
}


abstract class DescriptorReanimator(override val method: Method,
                                    override val model: SMTModel,
                                    override val context: ExecutionContext) : ModelReanimator<Descriptor> {
    override val memoryMappings = hashMapOf<Int, MutableMap<Int, Descriptor>>()

    override fun reanimate(term: Term, value: Term?): Descriptor = when {
        term.isPrimary -> reanimatePrimary(term, value)
        else -> reanimatePointer(term, value)
    }

    private fun reanimatePrimary(term: Term, value: Term?) = descriptor {
        if (value == null) default(term.type, false)
        else when (term.type) {
            is KexBool -> const((value as ConstBoolTerm).value)
            is KexByte -> const(value.numericValue.toByte())
            is KexChar -> const(value.numericValue.toChar())
            is KexShort -> const(value.numericValue.toShort())
            is KexInt -> const(value.numericValue.toInt())
            is KexLong -> const(value.numericValue.toLong())
            is KexFloat -> const(value.numericValue.toFloat())
            is KexDouble -> const(value.numericValue.toDouble())
            else -> unreachable { log.error("Trying to recover non-primary term as primary value: $value with type ${term.type}") }
        }
    }

    private fun reanimatePointer(term: Term, addr: Term?) = when (term.type) {
        is KexClass -> reanimateClass(term, addr)
        is KexArray -> reanimateArray(term, addr)
        is KexReference -> reanimateReference(term, addr)
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun reanimateClass(term: Term, addr: Term?) = descriptor {
        when (val address = (addr as? ConstIntTerm)?.value) {
            null, 0 -> default(term.type)
            else -> {
                val reanimatedType = resolveType(term.memspace, addr, term.type)
                if (term is ConstClassTerm) memory(term.memspace, address) { const(reanimatedType as KexClass) }
                ktassert(reanimatedType.isSubtypeOf(context.types, term.type)) {
                    log.error("Type resolving failed: actual type: ${term.type}, resolved type: $reanimatedType")
                }
                when (reanimatedType) {
                    is KexClass -> memory(term.memspace, address) { `object`(reanimatedType) }
                    is KexArray -> memory(term.memspace, address) { newArrayInstance(term.memspace, reanimatedType, addr) }
                    else -> unreachable {
                        log.error("Type resolving failed: actual type: ${term.type}, resolved type: $reanimatedType")
                    }
                }
            }
        }
    }

    private fun reanimateArray(term: Term, addr: Term?) = descriptor {
        when (val address = (addr as? ConstIntTerm)?.value) {
            null, 0 -> default(term.type)
            else -> {
                val arrayType = resolveType(term.memspace, addr, term.type) as? KexArray//(reanimateType(term.memspace, addr) ?: term.type) as? KexArray
                        ?: unreachable { log.error("Could not cast ${reanimateType(term.memspace, addr)} to array type") }
                memory(term.memspace, address) { newArrayInstance(term.memspace, arrayType, addr) }
            }
        }
    }

    private fun reanimateReference(term: Term, addr: Term?) = descriptor {
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
                val realIndex = (address - arrayAddr) / (elementType.bitsize / KexType.WORD)
                array[realIndex] = reanimatedValue
                array
            }
            is FieldTerm -> {
                val ownerRef = term.owner
                val ownerAddr = (reanimateFromAssignment(ownerRef) as ConstIntTerm).value
                val instance = memory(ownerRef.memspace, ownerAddr)
                    ?: return@descriptor default(term.type)

                val name = "${term.klass}.${term.fieldNameString}"
                val fieldValue = reanimateFromProperties(memspace, name, addr)

                val reanimatedValue = reanimateReferenceValue(term, fieldValue)
                val fieldName = term.fieldNameString
                val fieldType = (term.type as KexReference).reference

                when (instance) {
                    is FieldContainingDescriptor<*> -> {
                        instance[fieldName, fieldType] = reanimatedValue
                        instance
                    }
                    else -> unreachable { log.error("Unknown type of field owner") }
                }
            }
            else -> unreachable { log.error("Unknown reference term: $term with address $addr") }
        }
    }

    private fun reanimateReferenceValue(term: Term, value: Term?) = descriptor {
        val referencedType = (term.type as KexReference).reference
        if (value == null) return@descriptor default(term.type)

        when (value) {
            is ConstDoubleTerm -> const(value.value)
            is ConstFloatTerm -> const(value.value)
            else -> {
                val intVal = value.numericValue
                when (referencedType) {
                    is KexPointer -> reanimateReferencePointer(term, value)
                    is KexBool -> const(intVal.toBoolean())
                    is KexByte -> const(intVal.toByte())
                    is KexChar -> const(intVal.toChar())
                    is KexShort -> const(intVal.toShort())
                    is KexInt -> const(intVal)
                    is KexLong -> const(intVal.toLong())
                    is KexFloat -> const(intVal.toFloat())
                    is KexDouble -> const(intVal.toDouble())
                    else -> unreachable { log.error("Unknown type $referencedType") }
                }
            }
        }
    }

    private fun reanimateReferencePointer(term: Term, addr: Term?) = descriptor {
        val referencedType = (term.type as KexReference).reference
        val address = (addr as? ConstIntTerm)?.value ?: return@descriptor default(term.type)
        if (address == 0) return@descriptor default(term.type)

        when (val actualType = resolveType(term.memspace, addr, referencedType)) {
            is KexClass -> memory(term.memspace, address) { `object`(actualType) }
            is KexArray -> memory(term.memspace, address) {
                newArrayInstance(term.memspace, actualType, addr)
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }

    private fun newArrayInstance(memspace: Int, arrayType: KexArray, addr: Term?) = descriptor {
        val length = (reanimateFromProperties(memspace, "length", addr) as? ConstIntTerm)?.value ?: 0

        log.debug("Creating array of type $arrayType with size $length")
        array(length, arrayType.element)
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
