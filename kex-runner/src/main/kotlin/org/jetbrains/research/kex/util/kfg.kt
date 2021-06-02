package org.jetbrains.research.kex.util

import org.jetbrains.research.kfg.ir.MethodDesc
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
    return getCall(CallOpcode.Static(), valueOfMethod, wrapperClass, arrayOf(value), true)
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
    is CmpOpcode.Eq -> this === other
    is CmpOpcode.Neq -> this !== other
    else -> unreachable { log.error("Unknown opcode $opcode for object cmp") }
}

fun Number.apply(opcode: CmpOpcode, other: Number) = when (opcode) {
    is CmpOpcode.Eq -> this == other
    is CmpOpcode.Neq -> this != other
    is CmpOpcode.Lt -> this < other
    is CmpOpcode.Gt -> this > other
    is CmpOpcode.Le -> this <= other
    is CmpOpcode.Ge -> this >= other
    is CmpOpcode.Cmp -> this.compareTo(other)
    is CmpOpcode.Cmpg -> this.compareTo(other)
    is CmpOpcode.Cmpl -> this.compareTo(other)
}