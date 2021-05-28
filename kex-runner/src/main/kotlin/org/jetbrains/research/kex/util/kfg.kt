package org.jetbrains.research.kex.util

import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.PrimaryType

fun InstructionFactory.wrapValue(value: Value): Instruction {
    val wrapperType = cm.type.getWrapper(value.type as PrimaryType) as ClassType
    val wrapperClass = wrapperType.klass
    val valueOfMethod = wrapperClass.getMethod("valueOf", MethodDesc(arrayOf(value.type), wrapperType))
    return getCall(CallOpcode.Static(), valueOfMethod, wrapperClass, arrayOf(value), true)
}

fun Instruction.insertBefore(instructions: List<Instruction>) {
    this.parent.insertBefore(this, *instructions.toTypedArray())
}