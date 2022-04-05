package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.NameMapper
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionBuilder
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.compareTo
import org.jetbrains.research.kthelper.logging.log

fun Type.asArray(tf: TypeFactory) = tf.getArrayType(this)
val Type.javaDesc get() = this.name.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

fun Package.isParent(klass: Class) = isParent(klass.pkg)

fun InstructionBuilder.wrapValue(value: Value): Instruction {
    val wrapperType = cm.type.getWrapper(value.type as PrimaryType) as ClassType
    val wrapperClass = wrapperType.klass
    val valueOfMethod = wrapperClass.getMethod("valueOf", MethodDesc(arrayOf(value.type), wrapperType))
    return valueOfMethod.staticCall(wrapperClass, arrayOf(value))
}

fun Instruction.insertBefore(instructions: List<Instruction>) {
    this.parent.insertBefore(this, *instructions.toTypedArray())
}
fun Instruction.insertAfter(instructions: List<Instruction>) {
    this.parent.insertAfter(this, *instructions.toTypedArray())
}

fun Any?.cmp(opcode: CmpOpcode, other: Any?) = when {
    this is Number && other is Number -> this.apply(opcode, other)
    else -> this.apply(opcode, other)
}

fun Any?.apply(opcode: CmpOpcode, other: Any?) = when (opcode) {
    CmpOpcode.EQ -> this === other
    CmpOpcode.NEQ -> this !== other
    else -> unreachable { log.error("Unknown opcode $opcode for object cmp") }
}

fun Number.apply(opcode: CmpOpcode, other: Number) = when (opcode) {
    CmpOpcode.EQ -> this == other
    CmpOpcode.NEQ -> this != other
    CmpOpcode.LT -> this < other
    CmpOpcode.GT  -> this > other
    CmpOpcode.LE -> this <= other
    CmpOpcode.GE -> this >= other
    CmpOpcode.CMP -> this.compareTo(other)
    CmpOpcode.CMPG -> this.compareTo(other)
    CmpOpcode.CMPL -> this.compareTo(other)
}

fun NameMapper.parseValue(valueName: String): Value {
    val values = method.cm.value
    return getValue(valueName) ?: when {
        valueName.matches(Regex("\\d+")) -> values.getInt(valueName.toInt())
        valueName.matches(Regex("\\d+.\\d+")) -> values.getDouble(valueName.toDouble())
        valueName.matches(Regex("-\\d+")) -> values.getInt(valueName.toInt())
        valueName.matches(Regex("-\\d+.\\d+")) -> values.getDouble(valueName.toDouble())
        valueName.matches(Regex("\".*\"")) -> values.getString(valueName.substring(1, valueName.lastIndex))
        valueName.matches(Regex("\"[\n\\s]*\"")) -> values.getString(valueName.substring(1, valueName.lastIndex))
        valueName.matches(Regex(".*(/.*)+.class")) -> values.getClass("L${valueName.removeSuffix(".class")};")
        valueName == "null" -> values.nullConstant
        valueName == "true" -> values.trueConstant
        valueName == "false" -> values.falseConstant
        else -> unreachable { log.error("Unknown value name $valueName for object cmp") }
    }
}

val SystemTypeNames.unmodifiableCollection get() = "java/util/Collections\$UnmodifiableCollection"
val SystemTypeNames.unmodifiableList get() = "java/util/Collections\$UnmodifiableList"
val SystemTypeNames.unmodifiableSet get() = "java/util/Collections\$UnmodifiableSet"
val SystemTypeNames.unmodifiableMap get() = "java/util/Collections\$UnmodifiableMap"
val SystemTypeNames.charSequence get() = "java/lang/CharSequence"
val SystemTypeNames.field get() = "java/lang/reflect/Field"

val SystemTypeNames.classLoader get() = "java/lang/ClassLoader"
val ClassManager.classLoaderClass get() = this[SystemTypeNames.classLoader]
val TypeFactory.classLoaderType get() = getRefType(SystemTypeNames.classLoader)


fun Type.getAllSubtypes(tf: TypeFactory): Set<Type> = when (this) {
    is ClassType -> tf.cm.getAllSubtypesOf(this.klass).map { it.type }.toSet()
    is ArrayType -> this.component.getAllSubtypes(tf).map { tf.getArrayType(it) }.toSet()
    else -> setOf()
}

val Type.visibility: Visibility get() = when (this) {
    is ClassType -> this.klass.visibility
    is ArrayType -> this.component.visibility
    else -> Visibility.PUBLIC
}

fun KexType.getVisibility(tf: TypeFactory) = this.getKfgType(tf).visibility

fun parseAsConcreteType(typeFactory: TypeFactory, name: String): KexType? {
    val type = parseStringToType(typeFactory, name)
    return when {
        type.isConcrete -> type.kexType
        else -> null
    }
}
