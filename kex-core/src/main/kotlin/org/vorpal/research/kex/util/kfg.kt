@file:Suppress("unused", "UnusedReceiverParameter")

package org.vorpal.research.kex.util

import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.value.NameMapper
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InstructionBuilder
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kfg.type.ByteType
import org.vorpal.research.kfg.type.CharType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.DoubleType
import org.vorpal.research.kfg.type.FloatType
import org.vorpal.research.kfg.type.IntType
import org.vorpal.research.kfg.type.LongType
import org.vorpal.research.kfg.type.PrimitiveType
import org.vorpal.research.kfg.type.ShortType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.boolWrapper
import org.vorpal.research.kfg.type.byteWrapper
import org.vorpal.research.kfg.type.charWrapper
import org.vorpal.research.kfg.type.doubleWrapper
import org.vorpal.research.kfg.type.floatWrapper
import org.vorpal.research.kfg.type.intWrapper
import org.vorpal.research.kfg.type.longWrapper
import org.vorpal.research.kfg.type.parseStringToType
import org.vorpal.research.kfg.type.shortWrapper
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.LRUCache
import org.vorpal.research.kthelper.compareTo
import org.vorpal.research.kthelper.logging.log

val Type.javaDesc get() = this.name.javaString

fun Package.isParent(klass: Class) = isParent(klass.pkg)

fun InstructionBuilder.wrapValue(value: Value): Instruction {
    val wrapperType = cm.type.getWrapper(value.type as PrimitiveType) as ClassType
    val wrapperClass = wrapperType.klass
    val valueOfMethod = wrapperClass.getMethod("valueOf", wrapperType, value.type)
    return valueOfMethod.staticCall(wrapperClass, listOf(value))
}

fun InstructionBuilder.wrapUpValue(value: Value): Instruction {
    val (wrapperType, argType) = when (value.type) {
        is BoolType -> types.boolWrapper to BoolType
        is ByteType -> types.intWrapper to IntType
        is CharType -> types.intWrapper to IntType
        is ShortType -> types.intWrapper to IntType
        is IntType -> types.intWrapper to IntType
        is LongType -> types.longWrapper to LongType
        is FloatType -> types.floatWrapper to FloatType
        is DoubleType -> types.doubleWrapper to DoubleType
        else -> unreachable { log.error("Non-primitive value") }
    }
    val wrapperClass = (wrapperType as ClassType).klass
    val valueOfMethod = wrapperClass.getMethod("valueOf", wrapperType, argType)
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
        valueName.matches(Regex("\".*\"", RegexOption.DOT_MATCHES_ALL)) -> values.getString(valueName.substring(1, valueName.lastIndex))
        valueName.matches(Regex(".*(/.*)+.class")) -> values.getClass("L${valueName.removeSuffix(".class")};")
        valueName == "null" -> values.nullConstant
        valueName == "true" -> values.trueConstant
        valueName == "false" -> values.falseConstant
        else -> null
    }
}

fun Type.getAllSubtypes(tf: TypeFactory): Set<Type> = when (this) {
    is ClassType -> tf.cm.getAllSubtypesOf(this.klass).mapTo(mutableSetOf()) { it.asType }
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

val String.asmString get() = replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
val String.javaString get() = replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

fun Class.getCtor(vararg argTypes: Type) =
    getMethod("<init>", cm.type.voidType, *argTypes)

private object SubTypeInfoCache {
    private val subtypeCache = LRUCache<Pair<String, String>, Boolean>(100_000U)
    fun check(lhv: Type, rhv: Type): Boolean {
        val key = lhv.toString() to rhv.toString()
        return subtypeCache[key] ?: lhv.isSubtypeOf(rhv, outerClassBehavior = false).also {
            subtypeCache[key] = it
        }
    }
}

fun Type.isSubtypeOfCached(other: Type): Boolean = SubTypeInfoCache.check(this, other)

fun Field.isOuterThis(): Boolean {
    return klass.outerClass != null && name.matches("this\\$\\d+".toRegex())  && type == klass.outerClass!!.asType
}
