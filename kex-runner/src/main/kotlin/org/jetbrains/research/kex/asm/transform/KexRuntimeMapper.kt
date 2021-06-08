package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.util.KexRuntimeManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.visitor.MethodVisitor

class KexRuntimeMapper(override val cm: ClassManager) : MethodVisitor {
    private val rt = KexRuntimeManager()

    override fun cleanup() {}

    override fun visit(method: Method) {
        method.flatten().forEach { inst ->
            val remapped = inst.operands.mapNotNull { value ->
                value.remapped()?.let { value to it }
            }.toMap()
            if (remapped.isNotEmpty())  {
                val copy = inst.update(remapped)
                inst.replaceWith(copy)
            }
        }
        super.visit(method)
        method.slotTracker.rerun()
    }

    override fun visitCallInst(inst: CallInst) {
        val newKlass = rt[inst.klass] ?: return
        val newMethod = newKlass.getMethod(inst.method.name, inst.method.desc.remapped())
        val copy = when {
            inst.isStatic -> instructions.getCall(inst.opcode, newMethod, newKlass, inst.args.toTypedArray(), inst.isNameDefined)
            else -> instructions.getCall(inst.opcode, newMethod, newKlass, inst.callee, inst.args.toTypedArray(), inst.isNameDefined)
        }
        inst.replaceWith(copy)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val newField = inst.field.remapped() ?: return
        val copy = instructions.getFieldLoad(newField)
        inst.replaceWith(copy)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val newField = inst.field.remapped() ?: return
        val copy = instructions.getFieldStore(newField, inst.value)
        inst.replaceWith(copy)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val remappedType = inst.targetType.remapped() ?: return
        val copy = instructions.getInstanceOf(remappedType, inst.operand)
        inst.replaceWith(copy)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val remappedType = inst.type.remapped() ?: return
        val copy = instructions.getNewArray(remappedType, inst.dimensions.toTypedArray())
        inst.replaceWith(copy)
    }

    override fun visitNewInst(inst: NewInst) {
        val remappedType = inst.type.remapped() ?: return
        val copy = instructions.getNew(remappedType)
        inst.replaceWith(copy)
    }

    private fun Instruction.replaceWith(other: Instruction) {
        replaceAllUsesWith(other)
        parent.insertBefore(this, other)
        parent.remove(this)
    }

    private fun Field.remapped(): Field? {
        val newKlass = rt[klass] ?: return null
        return newKlass.getField(name, type.remapped() ?: type)
    }

    private fun MethodDesc.remapped() = MethodDesc(
        args.map { it.remapped() ?: it }.toTypedArray(), retval.remapped() ?: retval
    )

    private fun Type.remapped(): Type? = when(this) {
        is ClassType -> rt[klass]?.let { types.getRefType(it) }
        is ArrayType -> component.remapped()?.let { types.getArrayType(it) }
        else -> null
    }

    private fun Value.remapped(): Value? {
        val mappedType = type.remapped() ?: return null
        return when (this) {
            is ThisRef -> values.getThis(mappedType)
            is Argument -> values.getArgument(this.index, this.method, mappedType)
            is StringConstant -> StringConstant(this.value, mappedType)
            is ClassConstant -> ClassConstant(mappedType)
            else -> null
        }
    }
}