package org.jetbrains.research.kex.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.list
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.modules.serializersModuleOf
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc

@ImplicitReflectionSerializer
fun getKfgSerialModule(cm: ClassManager): SerialModule {
    ClassSerializer.cm = cm
    MethodSerializer.cm = cm
    return serializersModuleOf(mapOf(
            //BinaryOpcodes
            BinaryOpcode::class to BinaryOpcodeSerializer,
            BinaryOpcode.Add::class to BinaryOpcodeSerializer,
            BinaryOpcode.Sub::class to BinaryOpcodeSerializer,
            BinaryOpcode.Mul::class to BinaryOpcodeSerializer,
            BinaryOpcode.Div::class to BinaryOpcodeSerializer,
            BinaryOpcode.Rem::class to BinaryOpcodeSerializer,
            BinaryOpcode.Shl::class to BinaryOpcodeSerializer,
            BinaryOpcode.Shr::class to BinaryOpcodeSerializer,
            BinaryOpcode.Ushr::class to BinaryOpcodeSerializer,
            BinaryOpcode.And::class to BinaryOpcodeSerializer,
            BinaryOpcode.Or::class to BinaryOpcodeSerializer,
            BinaryOpcode.Xor::class to BinaryOpcodeSerializer,
            //CmpOpcodes
            CmpOpcode::class to CmpOpcodeSerializer,
            CmpOpcode.Eq::class to CmpOpcodeSerializer,
            CmpOpcode.Neq::class to CmpOpcodeSerializer,
            CmpOpcode.Lt::class to CmpOpcodeSerializer,
            CmpOpcode.Gt::class to CmpOpcodeSerializer,
            CmpOpcode.Le::class to CmpOpcodeSerializer,
            CmpOpcode.Ge::class to CmpOpcodeSerializer,
            CmpOpcode.Cmp::class to CmpOpcodeSerializer,
            CmpOpcode.Cmpg::class to CmpOpcodeSerializer,
            CmpOpcode.Cmpl::class to CmpOpcodeSerializer,
            //CallOpcodes
            CallOpcode::class to CallOpcodeSerializer,
            CallOpcode.Interface::class to CallOpcodeSerializer,
            CallOpcode.Virtual::class to CallOpcodeSerializer,
            CallOpcode.Static::class to CallOpcodeSerializer,
            CallOpcode.Special::class to CallOpcodeSerializer,
            //Classes
            Class::class to ClassSerializer,
            ConcreteClass::class to ClassSerializer,
            OuterClass::class to ClassSerializer,
            // other classes
            Location::class to LocationSerializer,
            Method::class to MethodSerializer
    ))
}

@Serializer(forClass = BinaryOpcode::class)
object BinaryOpcodeSerializer : KSerializer<BinaryOpcode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BinaryOpcode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): BinaryOpcode {
        val opcode = decoder.decodeString()
        return BinaryOpcode.parse(opcode)
    }
}

@Serializer(forClass = CmpOpcode::class)
object CmpOpcodeSerializer : KSerializer<CmpOpcode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CmpOpcode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): CmpOpcode {
        val opcode = decoder.decodeString()
        return CmpOpcode.parse(opcode)
    }
}

@Serializer(forClass = CallOpcode::class)
object CallOpcodeSerializer : KSerializer<CallOpcode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveDescriptor("opcode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CallOpcode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): CallOpcode {
        val opcode = decoder.decodeString()
        return CallOpcode.parse(opcode)
    }
}

@Serializer(forClass = Location::class)
@ImplicitReflectionSerializer
object LocationSerializer : KSerializer<Location> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("Location") {
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
                CompositeDecoder.READ_DONE -> break@loop
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

@Serializer(forClass = Class::class)
internal object ClassSerializer : KSerializer<Class> {
    lateinit var cm: ClassManager

    override val descriptor: SerialDescriptor
        get() = PrimitiveDescriptor("fullname", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Class) {
        encoder.encodeString(value.fullname)
    }

    override fun deserialize(decoder: Decoder) = cm[decoder.decodeString()]
}

@ImplicitReflectionSerializer
@Serializer(forClass = Method::class)
internal object MethodSerializer : KSerializer<Method> {
    lateinit var cm: ClassManager

    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("Method") {
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
        output.encodeSerializableElement(descriptor, 3, String.serializer().list, value.argTypes.map { it.asmDesc }.toList())
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
                CompositeDecoder.READ_DONE -> break@loop
                0 -> klass = input.decodeSerializableElement(descriptor, i, ClassSerializer)
                1 -> name = input.decodeStringElement(descriptor, i)
                2 -> retval = parseDesc(cm.type, input.decodeStringElement(descriptor, i))
                3 -> argTypes = input.decodeSerializableElement(descriptor, i, String.serializer().list)
                        .map { parseDesc(cm.type, it) }
                        .toTypedArray()
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return klass.getMethod(name, MethodDesc(argTypes, retval))
    }
}