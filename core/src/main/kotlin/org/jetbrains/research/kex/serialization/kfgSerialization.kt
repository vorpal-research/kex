package org.jetbrains.research.kex.serialization

import kotlinx.serialization.*
import kotlinx.serialization.internal.HexConverter
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.internal.StringDescriptor
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.modules.serializersModuleOf
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc

val kfgSerialModule: SerialModule
    get() = serializersModuleOf(mapOf(
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
            // other classes
            Location::class to LocationSerializer,
            Class::class to ClassSerializer,
            Method::class to MethodSerializer
    ))

private val String.hexString: String get() = HexConverter.printHexBinary(this.toByteArray())
private val String.unhexString: String get() = String(HexConverter.parseHexBinary(this))

@Serializer(forClass = BinaryOpcode::class)
object BinaryOpcodeSerializer : KSerializer<BinaryOpcode> {
    override val descriptor: SerialDescriptor
        get() = StringDescriptor.withName("opcode")

    override fun serialize(encoder: Encoder, obj: BinaryOpcode) {
        encoder.encodeString(obj.name.hexString)
    }

    override fun deserialize(decoder: Decoder): BinaryOpcode {
        val opcode = decoder.decodeString().unhexString
        return BinaryOpcode.parse(opcode)
    }
}

@Serializer(forClass = CmpOpcode::class)
object CmpOpcodeSerializer : KSerializer<CmpOpcode> {
    override val descriptor: SerialDescriptor
        get() = StringDescriptor.withName("opcode")

    override fun serialize(encoder: Encoder, obj: CmpOpcode) {
        encoder.encodeString(obj.name.hexString)
    }

    override fun deserialize(decoder: Decoder): CmpOpcode {
        val opcode = decoder.decodeString().unhexString
        return CmpOpcode.parse(opcode)
    }
}

@Serializer(forClass = CallOpcode::class)
object CallOpcodeSerializer : KSerializer<CallOpcode> {
    override val descriptor: SerialDescriptor
        get() = StringDescriptor.withName("opcode")

    override fun serialize(encoder: Encoder, obj: CallOpcode) {
        encoder.encodeString(obj.name.hexString)
    }

    override fun deserialize(decoder: Decoder): CallOpcode {
        val opcode = decoder.decodeString().unhexString
        return CallOpcode.parse(opcode)
    }
}

@Serializer(forClass = Location::class)
object LocationSerializer : KSerializer<Location> {
    override val descriptor: SerialDescriptor
        get() = object : SerialClassDescImpl("Location") {
            init {
                addElement("package")
                addElement("file")
                addElement("line")
            }
        }

    override fun serialize(encoder: Encoder, obj: Location) {
        val output = encoder.beginStructure(descriptor)
        output.encodeStringElement(descriptor, 0, obj.`package`.toString().hexString)
        output.encodeStringElement(descriptor, 1, obj.file.hexString)
        output.encodeIntElement(descriptor, 2, obj.line)
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
                0 -> `package` = Package(input.decodeStringElement(descriptor, i).unhexString)
                1 -> file = input.decodeStringElement(descriptor, i).unhexString
                2 -> line = input.decodeIntElement(descriptor, i)
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return Location(`package`, file, line)
    }
}

@Serializer(forClass = Class::class)
object ClassSerializer : KSerializer<Class> {
    lateinit var cm: ClassManager

    override val descriptor: SerialDescriptor
        get() = StringDescriptor.withName("fullname")

    override fun serialize(encoder: Encoder, obj: Class) {
        encoder.encodeString(obj.fullname.hexString)
    }

    override fun deserialize(decoder: Decoder): Class {
        val fullname = decoder.decodeString().unhexString
        return cm.getByName(fullname)
    }
}

@Serializer(forClass = Method::class)
object MethodSerializer : KSerializer<Method> {
    lateinit var cm: ClassManager

    override val descriptor: SerialDescriptor
        get() = object : SerialClassDescImpl("Method") {
            init {
                addElement("class")
                addElement("name")
                addElement("retval")
                addElement("arguments")
            }
        }

    override fun serialize(encoder: Encoder, obj: Method) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, ClassSerializer, obj.`class`)
        output.encodeStringElement(descriptor, 1, obj.name.hexString)
        output.encodeStringElement(descriptor, 2, obj.returnType.asmDesc.hexString)
        output.encodeSerializableElement(descriptor, 3, StringSerializer.list, obj.argTypes.map { it.asmDesc.hexString }.toList())
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
                1 -> name = input.decodeStringElement(descriptor, i).unhexString
                2 -> retval = parseDesc(cm.type, input.decodeStringElement(descriptor, i).unhexString)
                3 -> argTypes = input.decodeSerializableElement(descriptor, i, StringSerializer.list)
                        .map { parseDesc(cm.type, it.unhexString) }
                        .toTypedArray()
                else -> throw SerializationException("Unknown index $i")
            }
        }
        input.endStructure(descriptor)
        return klass.getMethod(name, MethodDesc(argTypes, retval))
    }
}