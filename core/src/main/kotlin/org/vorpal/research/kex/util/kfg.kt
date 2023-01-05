package org.vorpal.research.kex.util

import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.ktype.type
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.value.NameMapper
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InstructionBuilder
import org.vorpal.research.kfg.type.*
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.logging.log

val Type.javaDesc get() = this.name.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

fun Package.isParent(klass: Class) = isParent(klass.pkg)

fun InstructionBuilder.wrapValue(value: Value): Instruction {
    val wrapperType = cm.type.getWrapper(value.type as PrimitiveType) as ClassType
    val wrapperClass = wrapperType.klass
    val valueOfMethod = wrapperClass.getMethod("valueOf", wrapperType, value.type)
    return valueOfMethod.staticCall(wrapperClass, listOf(value))
}

fun Instruction.insertBefore(instructions: List<Instruction>) {
    this.parent.insertBefore(this, *instructions.toTypedArray())
}

fun Instruction.insertBefore(vararg instructions: Instruction) {
    this.parent.insertBefore(this, *instructions)
}

fun Instruction.insertAfter(instructions: List<Instruction>) {
    this.parent.insertAfter(this, *instructions.toTypedArray())
}

fun Instruction.insertAfter(vararg instructions: Instruction) {
    this.parent.insertAfter(this, *instructions)
}

val Instruction.next: Instruction? get() = parent.instructions.getOrNull(parent.indexOf(this) + 1)
val Instruction.previous: Instruction? get() = parent.instructions.getOrNull(parent.indexOf(this) - 1)

fun Any?.cmp(opcode: CmpOpcode, other: Any?) = when {
    this is Number && other is Number -> this.apply(opcode, other)
    this is Char && other is Number -> this.apply(opcode, other)
    this is Number && other is Char -> this.apply(opcode, other)
    this is Char && other is Char -> this.apply(opcode, other)
    else -> this.apply(opcode, other)
}

fun Any?.apply(opcode: CmpOpcode, other: Any?) = when (opcode) {
    CmpOpcode.EQ -> this === other
    CmpOpcode.NEQ -> this !== other
    else -> unreachable { log.error("Unknown opcode $opcode for object cmp: lhv = $this, other = $other") }
}

fun Number.apply(opcode: CmpOpcode, other: Number) = when (opcode) {
    CmpOpcode.EQ -> this == other
    CmpOpcode.NEQ -> this != other
    CmpOpcode.LT -> this < other
    CmpOpcode.GT -> this > other
    CmpOpcode.LE -> this <= other
    CmpOpcode.GE -> this >= other
    CmpOpcode.CMP -> this.compareTo(other)
    CmpOpcode.CMPG -> this.compareTo(other)
    CmpOpcode.CMPL -> this.compareTo(other)
}

fun Number.apply(opcode: CmpOpcode, other: Char) = this.apply(opcode, other.code)
fun Char.apply(opcode: CmpOpcode, other: Number) = this.code.apply(opcode, other)
fun Char.apply(opcode: CmpOpcode, other: Char) = this.code.apply(opcode, other.code)

fun NameMapper.parseValue(valueName: String): Value =
    parseValueOrNull(valueName) ?: unreachable { log.error("Unknown value name $valueName for object cmp") }

fun NameMapper.parseValueOrNull(valueName: String): Value? {
    val values = method.cm.value
    return getValue(valueName) ?: when {
        valueName.matches(Regex("-?\\d+L")) -> values.getLong(valueName.toLong())
        valueName.matches(Regex("-?\\d+b")) -> values.getByte(valueName.toByte())
        valueName.matches(Regex("-?\\d+c")) -> values.getChar(valueName.toInt().toChar())
        valueName.matches(Regex("-?\\d+s")) -> values.getShort(valueName.toShort())
        valueName.matches(Regex("-?\\d+")) -> values.getInt(valueName.toInt())
        valueName.matches(Regex("-?\\d+.\\d+f")) -> values.getFloat(valueName.toFloat())
        valueName.matches(Regex("-?\\d+.\\d+")) -> values.getDouble(valueName.toDouble())
        valueName.matches(Regex("\".*\"")) -> values.getString(valueName.substring(1, valueName.lastIndex))
        valueName.matches(Regex("\"[\n\\s]*\"")) -> values.getString(valueName.substring(1, valueName.lastIndex))
        valueName.matches(Regex(".*(/.*)+.class")) -> values.getClass("L${valueName.removeSuffix(".class")};")
        valueName == "null" -> values.nullConstant
        valueName == "true" -> values.trueConstant
        valueName == "false" -> values.falseConstant
        else -> null
    }
}

fun Type.getAllSubtypes(tf: TypeFactory): Set<Type> = when (this) {
    is ClassType -> tf.cm.getAllSubtypesOf(this.klass).mapTo(mutableSetOf()) { it.type }
    is ArrayType -> this.component.getAllSubtypes(tf).mapTo(mutableSetOf()) { tf.getArrayType(it) }
    else -> setOf()
}


fun parseAsConcreteType(typeFactory: TypeFactory, name: String): KexType? {
    val type = parseStringToType(typeFactory, name)
    return when {
        type.isConcrete -> type.kexType
        else -> null
    }
}

fun TypeFactory.getPrimitive(type: Type): Type = when (type) {
    boolWrapper -> boolType
    byteWrapper -> byteType
    charWrapper -> charType
    shortWrapper -> shortType
    intWrapper -> intType
    longWrapper -> longType
    floatWrapper -> floatType
    doubleWrapper -> doubleType
    else -> unreachable("Unknown primary type $type")
}

val SystemTypeNames.abstractCollectionClass: String get() = "java/util/AbstractCollection"
val SystemTypeNames.abstractListClass: String get() = "java/util/AbstractList"
val SystemTypeNames.abstractQueueClass: String get() = "java/util/AbstractQueue"
val SystemTypeNames.abstractSetClass: String get() = "java/util/AbstractSet"
val SystemTypeNames.abstractMapClass: String get() = "java/util/AbstractMap"

val ClassManager.abstractCollectionClass get() = this[SystemTypeNames.abstractCollectionClass]
val ClassManager.abstractListClass get() = this[SystemTypeNames.abstractListClass]
val ClassManager.abstractQueueClass get() = this[SystemTypeNames.abstractQueueClass]
val ClassManager.abstractSetClass get() = this[SystemTypeNames.abstractSetClass]
val ClassManager.abstractMapClass get() = this[SystemTypeNames.abstractMapClass]
