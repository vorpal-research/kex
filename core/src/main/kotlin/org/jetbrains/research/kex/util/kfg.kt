package org.jetbrains.research.kex.util

import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.PrimaryType
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.compareTo
import org.jetbrains.research.kthelper.logging.log

fun InstructionFactory.wrapValue(value: Value): Instruction {
    val wrapperType = cm.type.getWrapper(value.type as PrimaryType) as ClassType
    val wrapperClass = wrapperType.klass
    val valueOfMethod = wrapperClass.getMethod("valueOf", MethodDesc(arrayOf(value.type), wrapperType))
    return getCall(CallOpcode.STATIC, valueOfMethod, wrapperClass, arrayOf(value), true)
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

fun SlotTracker.parseValue(valueName: String): Value {
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
        else -> unreachable { log.error("Unknown value name $valueName for object cmp") }
    }
}