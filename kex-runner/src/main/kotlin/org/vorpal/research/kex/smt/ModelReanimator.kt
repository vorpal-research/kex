package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.FieldContainingDescriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexNull
import org.vorpal.research.kex.ktype.KexPointer
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.isJavaClass
import org.vorpal.research.kex.ktype.isString
import org.vorpal.research.kex.state.term.ArrayIndexTerm
import org.vorpal.research.kex.state.term.ClassAccessTerm
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.ConstDoubleTerm
import org.vorpal.research.kex.state.term.ConstFloatTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.ConstStringTerm
import org.vorpal.research.kex.state.term.FieldTerm
import org.vorpal.research.kex.state.term.StaticClassRefTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.numericValue
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.memspace
import org.vorpal.research.kex.util.getActualField
import org.vorpal.research.kex.util.isFinal
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.toBoolean
import org.vorpal.research.kthelper.tryOrNull
import sun.misc.Unsafe
import java.lang.reflect.Array

private val Term.isPointer get() = this.type is KexPointer
private val Term.isPrimitive get() = !this.isPointer

interface ModelReanimator<T> {
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

    fun reanimate(
        term: Term,
        value: Term? = model.assignments[term]
    ): T

    fun reanimateFromAssignment(term: Term): Term?
    fun reanimateFromMemory(memspace: Int, addr: Term?): Term?
    fun reanimateFromProperties(memspace: Int, name: String, addr: Term?): Term?
    fun reanimateFromArray(memspace: Int, array: Term, index: Term): Term?

    fun reanimateType(memspace: Int, addr: Term?): KexType? {
        val typeVar = reanimateFromProperties(memspace, "type", addr) ?: return null
        return reanimateType(typeVar)
    }

    fun reanimateType(typeVar: Term): KexType? {
        val binaryString = when (typeVar) {
            is ConstStringTerm -> typeVar.value
            else -> typeVar.numericValue.toInt().toString(2)
        }.reversed()
        for ((index, char) in binaryString.withIndex()) {
            if (char == '1') {
                return model.typeMap[term { const(index) }] ?: continue
            }
        }
        return null
    }

    fun reanimateString(memspace: Int, addr: Term?): Term? = model.strings[memspace]?.finalMemory?.get(addr)

    fun resolveType(memspace: Int, addr: Term?, default: KexType): KexType {
        val resolvedType = reanimateType(memspace, addr) ?: return default
        return when {
            resolvedType.isSubtypeOf(context.types, default) -> resolvedType
            else -> default
        }
    }
}

class ObjectReanimator(
    override val model: SMTModel,
    override val context: ExecutionContext
) : ModelReanimator<Any?> {
    companion object {
        private val UNSAFE = run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }

    private val randomizer get() = context.random

    override val memoryMappings = hashMapOf<Int, MutableMap<Int, Any?>>()

    override fun reanimate(term: Term, value: Term?): Any? = when {
        term.isPrimitive -> reanimatePrimary(term.type, value)
        else -> reanimatePointer(term, value)
    }

    override fun reanimateFromAssignment(term: Term) = model.assignments[term]
    override fun reanimateFromMemory(memspace: Int, addr: Term?) = model.memories[memspace]?.finalMemory?.get(addr)
    override fun reanimateFromProperties(memspace: Int, name: String, addr: Term?) =
        model.properties[memspace]?.get(name)?.finalMemory?.get(addr)

    override fun reanimateFromArray(memspace: Int, array: Term, index: Term): Term? =
        model.arrays[memspace]?.get(array)?.finalMemory?.get(index)

    private fun reanimatePrimary(type: KexType, value: Term?): Any? {
        if (value == null) return randomizer.next(loader.loadClass(context.types, type))
        return when (type) {
            is KexBool -> (value as ConstBoolTerm).value
            is KexByte -> value.numericValue.toByte()
            is KexChar -> value.numericValue.toInt().toChar()
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
        is KexNull -> null
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun reanimateClass(term: Term, addr: Term?): Any? {
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val type = resolveType(term.memspace, addr, term.type)
        return memory(term.memspace, address) {
            val fallback = {
                UNSAFE.allocateInstance(
                    loader.loadClass(
                        context.types,
                        instantiationManager.getConcreteType(type, context.cm, context.accessLevel, context.random)
                    )
                )
            }
            when {
                term.type.isString && model.hasStrings -> reanimateString(term.memspace, addr)
                term.type.isJavaClass -> {
                    val typeIndex = reanimateFromProperties(term.memspace, ConstClassTerm.TYPE_INDEX_PROPERTY, addr)
                        ?: return@memory fallback()
                    val klassType = reanimateType(typeIndex)
                        ?: return@memory fallback()
                    loader.loadClass(context.types, klassType)
                }
                else -> fallback()
            }
        }
    }

    private fun arrayLength(any: Any?) = when (any) {
        is BooleanArray -> any.size
        is ByteArray -> any.size
        is CharArray -> any.size
        is ShortArray -> any.size
        is IntArray -> any.size
        is LongArray -> any.size
        is FloatArray -> any.size
        is DoubleArray -> any.size
        is kotlin.Array<*> -> any.size
        else -> 0
    }

    private fun reanimateArray(term: Term, addr: Term?): Any? {
        val address = (addr as? ConstIntTerm)?.value ?: return null
        if (address == 0) return null

        val instance = newArrayInstance(term.type as KexArray, addr)
        for (i in 0 until arrayLength(instance)) {
            reanimate(term { term[i] }, null)
        }
        return memory(term.memspace, address, instance)
    }

    private fun reanimateReference(term: Term, addr: Term?): Any? {
        var memspace = term.memspace
        return when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                memspace = arrayRef.memspace

                val arrayAddr = reanimateFromAssignment(arrayRef) ?: return null
                val array = memory(arrayRef.memspace, arrayAddr.numericValue.toInt()) ?: return null

                val index = when (term.index) {
                    is ConstIntTerm -> term.index
                    else -> reanimateFromAssignment(term.index)
                } ?: return null
                val refValue = reanimateFromArray(memspace, arrayAddr, index)

                val reanimatedValue = reanimateReferenceValue(term, refValue)
                Array.set(array, index.numericValue.toInt(), reanimatedValue)
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

                        val kfgClass = context.cm[type.klass]
                        val `class` = tryOrNull { loader.loadClass(kfgClass.canonicalDesc) } ?: return null
                        val instance = memory(objectRef.memspace, objectAddr) ?: return null
                        instance to `class`
                    }
                }
                val name = "${term.klass}.${term.fieldName}"
                val fieldValue = reanimateFromProperties(memspace, name, addr)

                val fieldReflect = klass.getActualField(term.fieldName)
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
                    is KexByte -> intVal.toByte()
                    is KexChar -> intVal.toChar()
                    is KexShort -> intVal.toShort()
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
            is KexClass -> memory(term.memspace, address) {
                randomizer.nextOrNull(
                    loader.loadClass(
                        context.types,
                        type
                    )
                )
            }
            is KexArray -> {
                val memspace = term.memspace//referencedType.memspace
                val instance = newArrayInstance(referencedType, addr)
                for (i in 0 until arrayLength(instance)) {
                    val index = term { const(i) }
                    val refValue = reanimateFromArray(term.memspace, addr, index)

                    val reanimatedValue = reanimateReferenceValue(
                        ArrayIndexTerm(
                            KexReference(referencedType.element),
                            term { term.load() },
                            index
                        ), refValue
                    )
                    Array.set(instance, i, reanimatedValue)
                }
                memory(memspace, address, instance)
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }

    private fun newArrayInstance(type: KexArray, addr: Term?): Any? {
        val length = (reanimateFromProperties(type.memspace, "length", addr) as? ConstIntTerm)?.value ?: return null

        val actualType = resolveType(
            type.memspace,
            addr,
            type
        ) as? KexArray//(reanimateType(type.memspace, addr) ?: type) as? KexArray
            ?: unreachable { log.error("Non-array type in reanimate array") }
        val elementType = loader.loadClass(context.types, actualType.element)
        log.debug("Creating array of type {} with size {}", elementType, length)
        return Array.newInstance(elementType, length)
    }
}


abstract class DescriptorReanimator(
    override val model: SMTModel,
    override val context: ExecutionContext
) : ModelReanimator<Descriptor> {
    override val memoryMappings = hashMapOf<Int, MutableMap<Int, Descriptor>>()

    override fun reanimate(term: Term, value: Term?): Descriptor = when {
        term.isPrimitive -> reanimatePrimary(term, value)
        else -> reanimatePointer(term, value)
    }

    private fun reanimatePrimary(term: Term, value: Term?) = descriptor {
        if (value == null) default(term.type, false)
        else when (term.type) {
            is KexBool -> const((value as ConstBoolTerm).value)
            is KexByte -> const(value.numericValue.toByte())
            is KexChar -> const(value.numericValue.toInt().toChar())
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
        is KexNull -> descriptor { `null` }
        else -> unreachable { log.error("Trying to recover non-pointer term $term with type ${term.type} as pointer value") }
    }

    private fun reanimateClass(term: Term, addr: Term?) = descriptor {
        when (val address = (addr as? ConstIntTerm)?.value) {
            null, 0 -> default(term.type)
            else -> {
                val reanimatedType = resolveType(term.memspace, addr, term.type)
                if (term is StaticClassRefTerm) {
                    return@descriptor memory(term.memspace, address) { const(term.type as KexClass) }
                }
                ktassert(reanimatedType.isSubtypeOf(context.types, term.type)) {
                    log.error("Type resolving failed: actual type: ${term.type}, resolved type: $reanimatedType")
                }
                when (reanimatedType) {
                    is KexClass -> {
                        memory(term.memspace, address) {
                            when {
                                term.type.isString && model.hasStrings -> {
                                    val strValue =
                                        (reanimateString(term.memspace, addr) as? ConstStringTerm)?.value ?: ""
                                    string(strValue)
                                }
                                term.type.isJavaClass -> {
                                    val typeIndex =
                                        reanimateFromProperties(term.memspace, ConstClassTerm.TYPE_INDEX_PROPERTY, addr)
                                            ?: return@memory default(term.type, nullable = false)
                                    val klassType = reanimateType(typeIndex)
                                        ?: return@memory default(term.type, nullable = false)
                                    klass(klassType)
                                }
                                else -> `object`(reanimatedType)
                            }
                        }.also {
                            if (term is ClassAccessTerm) {
                                val operand = term.operand
                                val operandDesc = memory(
                                    operand.memspace,
                                    reanimateFromAssignment(operand)?.numericValue?.toInt() ?: 0
                                )
                                if (operandDesc != null && it is ObjectDescriptor) {
                                    operandDesc.klassDescriptor = it
                                }
                            }
                        }
                    }
                    is KexArray -> {
                        val res = memory(term.memspace, address) {
                            newArrayInstance(
                                term.memspace,
                                reanimatedType,
                                addr
                            )
                        } as ArrayDescriptor
                        for (i in 0 until res.length) {
                            val index = term { const(i) }
                            val refValue = reanimateFromArray(term.memspace, addr, index)

                            // todo: this is fucked up
                            val reanimatedValue = reanimateReferenceValue(
                                ArrayIndexTerm(
                                    KexReference(reanimatedType.element),
                                    term,
                                    index
                                ), refValue
                            )
                            res[i] = reanimatedValue
                        }
                        res
                    }
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
                val arrayType = resolveType(
                    term.memspace,
                    addr,
                    term.type
                ) as? KexArray//(reanimateType(term.memspace, addr) ?: term.type) as? KexArray
                    ?: unreachable {
                        log.error(
                            "Could not cast ${
                                resolveType(
                                    term.memspace,
                                    addr,
                                    term.type
                                )
                            } to array type"
                        )
                    }
                val res = memory(term.memspace, address) {
                    newArrayInstance(
                        term.memspace,
                        arrayType,
                        addr
                    )
                } as? ArrayDescriptor
                    ?: unreachable { log.error("Could not cast ${memory(term.memspace, address)} to array type") }
                for (i in 0 until res.length) {
                    reanimate(term { term[i] }, null)
                }
                res
            }
        }
    }

    private fun reanimateReference(term: Term, addr: Term?) = descriptor {
        var memspace = term.memspace
        when (term) {
            is ArrayIndexTerm -> {
                val arrayRef = term.arrayRef
                memspace = arrayRef.memspace

                val arrayAddr = reanimateFromAssignment(arrayRef) ?: return@descriptor default(term.type)
                val array = memory(arrayRef.memspace, arrayAddr.numericValue.toInt()) as? ArrayDescriptor
                    ?: unreachable {
                        log.error(
                            "Could not cast ${
                                memory(
                                    term.memspace,
                                    arrayAddr.numericValue.toInt()
                                )
                            } to array type"
                        )
                    }

                val index = when (term.index) {
                    is ConstIntTerm -> term.index
                    else -> reanimateFromAssignment(term.index)
                } ?: return@descriptor default(term.type)
                val refValue = reanimateFromArray(memspace, arrayAddr, index)

                val reanimatedValue = reanimateReferenceValue(term, refValue)
                array[index.numericValue.toInt()] = reanimatedValue
                array
            }
            is FieldTerm -> {
                val ownerRef = term.owner
                val ownerAddress = (reanimateFromAssignment(ownerRef) as ConstIntTerm).value
                val instance = memory(ownerRef.memspace, ownerAddress)
                    ?: return@descriptor default(term.type)

                val name = "${term.klass}.${term.fieldName}"
                val fieldValue = reanimateFromProperties(memspace, name, addr)

                val reanimatedValue = reanimateReferenceValue(term, fieldValue)
                val fieldName = term.fieldName
                val fieldType = (term.type as KexReference).reference

                when (instance) {
                    is FieldContainingDescriptor<*> -> {
                        instance[fieldName, fieldType] = reanimatedValue
                        instance
                    }
                    else -> {
                        reanimate(ownerRef)
                        unreachable { log.error("Unknown type of field owner") }
                    }
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
                    is KexChar -> const(intVal.toInt().toChar())
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

    private fun reanimateReferencePointer(term: Term, addr: Term?): Descriptor = descriptor {
        val referencedType = (term.type as KexReference).reference
        val address = (addr as? ConstIntTerm)?.value ?: return@descriptor default(term.type)
        if (address == 0) return@descriptor default(term.type)

        when (val actualType = resolveType(term.memspace, addr, referencedType)) {
            is KexClass -> memory(term.memspace, address) { `object`(actualType) }
            is KexArray -> {
                if (memory(term.memspace, address) != null)
                    return@descriptor memory(term.memspace, address)!!
                val res = memory(term.memspace, address) {
                    newArrayInstance(term.memspace, actualType, addr)
                } as ArrayDescriptor
                for (i in 0 until res.length) {
                    val index = term { const(i) }
                    val refValue = reanimateFromArray(term.memspace, addr, index)

                    val reanimatedValue = reanimateReferenceValue(
                        ArrayIndexTerm(
                            KexReference(actualType.element),
                            term { term.load() },
                            index
                        ), refValue
                    )
                    res[i] = reanimatedValue
                }
                res
            }
            else -> unreachable { log.error("Trying to recover reference pointer that is not pointer") }
        }
    }

    private fun newArrayInstance(memspace: Int, arrayType: KexArray, addr: Term?) = descriptor {
        val length = (reanimateFromProperties(memspace, "length", addr) as? ConstIntTerm)?.value ?: 0

        log.debug("Creating array of type {} with size {}", arrayType, length)
        array(length, arrayType.element)
    }
}

class FinalDescriptorReanimator(model: SMTModel, context: ExecutionContext) :
    DescriptorReanimator(model, context) {
    override fun reanimateFromAssignment(term: Term) = model.assignments[term]
    override fun reanimateFromMemory(memspace: Int, addr: Term?) = model.memories[memspace]?.finalMemory?.get(addr)
    override fun reanimateFromProperties(memspace: Int, name: String, addr: Term?) =
        model.properties[memspace]?.get(name)?.finalMemory?.get(addr)

    override fun reanimateFromArray(memspace: Int, array: Term, index: Term): Term? =
        model.arrays[memspace]?.get(array)?.finalMemory?.get(index)
}

class InitialDescriptorReanimator(model: SMTModel, context: ExecutionContext) :
    DescriptorReanimator(model, context) {
    override fun reanimateFromAssignment(term: Term) = model.assignments[term]
    override fun reanimateFromMemory(memspace: Int, addr: Term?) = model.memories[memspace]?.initialMemory?.get(addr)
    override fun reanimateFromProperties(memspace: Int, name: String, addr: Term?) =
        model.properties[memspace]?.get(name)?.initialMemory?.get(addr)

    override fun reanimateFromArray(memspace: Int, array: Term, index: Term): Term? =
        model.arrays[memspace]?.get(array)?.initialMemory?.get(index)
}
