package org.vorpal.research.kex.serialization

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
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.*
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.CallOpcode
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.parseDesc

@ExperimentalSerializationApi
fun getKfgSerialModule(cm: ClassManager, ctx: NameMapperContext): SerializersModule {
    return SerializersModule {
        contextual(BinaryOpcode::class, BinaryOpcodeSerializer)

        contextual(CmpOpcode::class, CmpOpcodeSerializer)

        contextual(CallOpcode::class, CallOpcodeSerializer)

        val klassSerializer = ClassSerializer(cm)
        contextual(Class::class, klassSerializer)
        contextual(ConcreteClass::class, klassSerializer.to())
        contextual(OuterClass::class, klassSerializer.to())

        contextual(Location::class, LocationSerializer)

        val methodSerializer = MethodSerializer(cm, klassSerializer)
        contextual(Method::class, methodSerializer)
        contextual(Instruction::class, InstructionSerializer(ctx, methodSerializer))
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = BinaryOpcode::class)
object BinaryOpcodeSerializer : KSerializer<BinaryOpcode> {
    override val descriptor = PrimitiveSerialDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BinaryOpcode) {
        encoder.encodeString(value.opcode)
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
    override val descriptor = PrimitiveSerialDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CmpOpcode) {
        encoder.encodeString(value.opcode)
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
    override val descriptor = PrimitiveSerialDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CallOpcode) {
        encoder.encodeString(value.opcode)
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
    override val descriptor = buildClassSerialDescriptor("Location") {
        element<String>("package")
        element<String>("file")
        element<Int>("line")
    }

    override fun serialize(encoder: Encoder, value: Location) {
        val output = encoder.beginStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.pkg.toString())
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
                0 -> `package` = Package.parse(input.decodeStringElement(descriptor, i))
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
internal class ClassSerializer(val cm: ClassManager) : KSerializer<Class> {

    override val descriptor = PrimitiveSerialDescriptor("fullname", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Class) {
        encoder.encodeString(value.fullName)
    }

    override fun deserialize(decoder: Decoder) = cm[decoder.decodeString()]
}

@ExperimentalSerializationApi
internal inline fun <reified T : Class> ClassSerializer.to() = object : KSerializer<T> {
    override val descriptor get() = this@to.descriptor

    override fun deserialize(decoder: Decoder): T = this@to.deserialize(decoder) as T

    override fun serialize(encoder: Encoder, value: T) {
        this@to.serialize(encoder, value)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = Method::class)
internal class MethodSerializer(val cm: ClassManager, val classSerializer: KSerializer<Class>) : KSerializer<Method> {
    override val descriptor = buildClassSerialDescriptor("Method") {
        element("class", classSerializer.descriptor)
        element<String>("name")
        element<String>("retval")
        element<List<String>>("arguments")
    }

    override fun serialize(encoder: Encoder, value: Method) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, classSerializer, value.klass)
        output.encodeStringElement(descriptor, 1, value.name)
        output.encodeStringElement(descriptor, 2, value.returnType.asmDesc)
        output.encodeSerializableElement(
            descriptor,
            3,
            ListSerializer(String.serializer()),
            value.argTypes.map { it.asmDesc }.toList()
        )
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
                0 -> klass = input.decodeSerializableElement(descriptor, i, classSerializer)
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

@ExperimentalSerializationApi
@Serializer(forClass = Instruction::class)
internal class InstructionSerializer(
    val ctx: NameMapperContext,
    val methodSerializer: KSerializer<Method>
) : KSerializer<Instruction> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Instruction") {
        element("method", methodSerializer.descriptor)
        element<String>("name")
    }

    override fun serialize(encoder: Encoder, value: Instruction) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, methodSerializer, value.parent.parent)
        output.encodeStringElement(descriptor, 1, "${value.name}")
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Instruction {
        val input = decoder.beginStructure(descriptor)
        lateinit var method: Method
        lateinit var name: String
        loop@ while (true) {
            when (val i = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> method = input.decodeSerializableElement(descriptor, i, methodSerializer)
                1 -> name = input.decodeStringElement(descriptor, i)
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return ctx.getMapper(method).getValue(name) as Instruction
    }
}
