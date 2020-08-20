package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc

@ExperimentalSerializationApi
fun getKfgSerialModule(cm: ClassManager): SerializersModule {
    ClassSerializer.cm = cm
    MethodSerializer.cm = cm
    return SerializersModule {
        contextual(BinaryOpcode::class, BinaryOpcodeSerializer)
        contextual(BinaryOpcode.Add::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Sub::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Mul::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Div::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Rem::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Shl::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Shr::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Ushr::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.And::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Or::class, BinaryOpcodeSerializer.to())
        contextual(BinaryOpcode.Xor::class, BinaryOpcodeSerializer.to())

        contextual(CmpOpcode::class, CmpOpcodeSerializer)
        contextual(CmpOpcode.Eq::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Neq::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Lt::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Gt::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Le::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Ge::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Cmp::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Cmpg::class, CmpOpcodeSerializer.to())
        contextual(CmpOpcode.Cmpl::class, CmpOpcodeSerializer.to())

        contextual(CallOpcode::class, CallOpcodeSerializer)
        contextual(CallOpcode.Interface::class, CallOpcodeSerializer.to())
        contextual(CallOpcode.Virtual::class, CallOpcodeSerializer.to())
        contextual(CallOpcode.Static::class, CallOpcodeSerializer.to())
        contextual(CallOpcode.Special::class, CallOpcodeSerializer.to())

        contextual(Class::class, ClassSerializer)
        contextual(ConcreteClass::class, ClassSerializer.to())
        contextual(OuterClass::class, ClassSerializer.to())

        contextual(Location::class, LocationSerializer)
        contextual(Method::class, MethodSerializer)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = BinaryOpcode::class)
object BinaryOpcodeSerializer : KSerializer<BinaryOpcode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BinaryOpcode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): BinaryOpcode {
        val opcode = decoder.decodeString()
        return BinaryOpcode.parse(opcode)
    }
}

@ExperimentalSerializationApi
inline fun <reified T : BinaryOpcode> BinaryOpcodeSerializer.to() = object : KSerializer<T> {
    override val descriptor get() = this@to.descriptor

    override fun deserialize(decoder: Decoder): T = this@to.deserialize(decoder) as T

    override fun serialize(encoder: Encoder, value: T) {
        this@to.serialize(encoder, value)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = CmpOpcode::class)
object CmpOpcodeSerializer : KSerializer<CmpOpcode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CmpOpcode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): CmpOpcode {
        val opcode = decoder.decodeString()
        return CmpOpcode.parse(opcode)
    }
}

@ExperimentalSerializationApi
inline fun <reified T : CmpOpcode> CmpOpcodeSerializer.to() = object : KSerializer<T> {
    override val descriptor get() = this@to.descriptor

    override fun deserialize(decoder: Decoder): T = this@to.deserialize(decoder) as T

    override fun serialize(encoder: Encoder, value: T) {
        this@to.serialize(encoder, value)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = CallOpcode::class)
object CallOpcodeSerializer : KSerializer<CallOpcode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CallOpcode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): CallOpcode {
        val opcode = decoder.decodeString()
        return CallOpcode.parse(opcode)
    }
}

@ExperimentalSerializationApi
inline fun <reified T : CallOpcode> CallOpcodeSerializer.to() = object : KSerializer<T> {
    override val descriptor get() = this@to.descriptor

    override fun deserialize(decoder: Decoder): T = this@to.deserialize(decoder) as T

    override fun serialize(encoder: Encoder, value: T) {
        this@to.serialize(encoder, value)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = Location::class)
object LocationSerializer : KSerializer<Location> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Location") {
            element<String>("package")
            element<String>("file")
            element<Int>("line")
        }

    override fun serialize(encoder: Encoder, value: Location) {
        val output = encoder.beginStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.`package`.toString())
        output.encodeStringElement(descriptor, 1, value.file)
        output.encodeIntElement(descriptor, 2, value.line)
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Location {
        val input = decoder.beginStructure(descriptor)
        lateinit var `package`: Package
        lateinit var file: String
        var line = 0
        loop@ while (true) {
            when (val i = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> `package` = Package(input.decodeStringElement(descriptor, i))
                1 -> file = input.decodeStringElement(descriptor, i)
                2 -> line = input.decodeIntElement(descriptor, i)
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return Location(`package`, file, line)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = Class::class)
object ClassSerializer : KSerializer<Class> {
    lateinit var cm: ClassManager

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("fullname", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Class) {
        encoder.encodeString(value.fullname)
    }

    override fun deserialize(decoder: Decoder) = cm[decoder.decodeString()]
}

@ExperimentalSerializationApi
inline fun <reified T : Class> ClassSerializer.to() = object : KSerializer<T> {
    override val descriptor get() = this@to.descriptor

    override fun deserialize(decoder: Decoder): T = this@to.deserialize(decoder) as T

    override fun serialize(encoder: Encoder, value: T) {
        this@to.serialize(encoder, value)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = Method::class)
internal object MethodSerializer : KSerializer<Method> {
    lateinit var cm: ClassManager

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Method") {
            element("class", ClassSerializer.descriptor)
            element<String>("name")
            element<String>("retval")
            element<List<String>>("arguments")
        }

    override fun serialize(encoder: Encoder, value: Method) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, ClassSerializer, value.`class`)
        output.encodeStringElement(descriptor, 1, value.name)
        output.encodeStringElement(descriptor, 2, value.returnType.asmDesc)
        output.encodeSerializableElement(descriptor, 3, ListSerializer(String.serializer()), value.argTypes.map { it.asmDesc }.toList())
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Method {
        val input = decoder.beginStructure(descriptor)
        lateinit var klass: Class
        lateinit var name: String
        lateinit var retval: Type
        lateinit var argTypes: Array<Type>
        loop@ while (true) {
            when (val i = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> klass = input.decodeSerializableElement(descriptor, i, ClassSerializer)
                1 -> name = input.decodeStringElement(descriptor, i)
                2 -> retval = parseDesc(cm.type, input.decodeStringElement(descriptor, i))
                3 -> argTypes = input.decodeSerializableElement(descriptor, i, ListSerializer(String.serializer()))
                        .map { parseDesc(cm.type, it) }
                        .toTypedArray()
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return klass.getMethod(name, MethodDesc(argTypes, retval))
    }
}