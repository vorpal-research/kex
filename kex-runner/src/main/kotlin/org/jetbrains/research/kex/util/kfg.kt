package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueFactory
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.PrimaryType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

fun Class.getMethod(name: String, returnType: Type, vararg argTypes: Type) =
    this.getMethod(name, MethodDesc(argTypes.toList().toTypedArray(), returnType))

fun ValueFactory.getNumberConstant(value: Number): Value = when (value) {
    is Int -> getIntConstant(value)
    is Float -> getFloatConstant(value)
    is Long -> getLongConstant(value)
    is Double -> getDoubleConstant(value)
    else -> unreachable {
        log.error("Unknown number: $value")
    }
}

val ClassManager.listClass get() = this["java/util/List"]
val ClassManager.arrayListClass get() = this["java/util/ArrayList"]

val TypeFactory.listType get() = cm.listClass.type
val TypeFactory.arrayListType get() = cm.arrayListClass.type

fun InstructionFactory.wrapValue(value: Value): Instruction {
    val wrapperType = cm.type.getWrapper(value.type as PrimaryType) as ClassType
    val wrapperClass = wrapperType.`class`
    val valueOfMethod = wrapperClass.getMethod("valueOf", MethodDesc(arrayOf(value.type), wrapperType))
    return getCall(CallOpcode.Static(), valueOfMethod, wrapperClass, arrayOf(value), true)
}

fun Instruction.insertBefore(instructions: List<Instruction>) {
    this.parent.insertBefore(this, *instructions.toTypedArray())
}