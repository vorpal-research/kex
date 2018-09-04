package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor

class PredicateBuilder : MethodVisitor {
    val tf = TermFactory
    val pf = PredicateFactory
    val predicateMap = hashMapOf<Instruction, Predicate>()
    val phiPredicateMap = hashMapOf<Pair<BasicBlock, Instruction>, Predicate>()
    val terminatorPredicateMap = hashMapOf<Pair<BasicBlock, TerminateInst>, Predicate>()

    override fun cleanup() {
        predicateMap.clear()
        phiPredicateMap.clear()
        terminatorPredicateMap.clear()
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val lhv = tf.getValue(inst)
        val ref = tf.getValue(inst.arrayRef)
        val indx = tf.getValue(inst.index)
        val arrayRef = tf.getArrayIndex(ref, indx)
        val load = tf.getArrayLoad(arrayRef)

        predicateMap[inst] = pf.getLoad(lhv, load, location = inst.location)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val ref = tf.getValue(inst.arrayRef)
        val indx = tf.getValue(inst.index)
        val arrayRef = tf.getArrayIndex(ref, indx)
        val value = tf.getValue(inst.value)

        predicateMap[inst] = pf.getArrayStore(arrayRef, value, location = inst.location)
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val lhv = tf.getValue(inst)
        val rhv = tf.getBinary(
                inst.opcode,
                tf.getValue(inst.lhv),
                tf.getValue(inst.rhv)
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
    }

    override fun visitBranchInst(inst: BranchInst) {
        val cond = tf.getValue(inst.cond)
        terminatorPredicateMap[inst.trueSuccessor to inst] = pf.getBoolean(
                cond,
                tf.getTrue(),
                location = inst.location
        )
        terminatorPredicateMap[inst.falseSuccessor to inst] = pf.getBoolean(
                cond,
                tf.getFalse(),
                location = inst.location
        )
    }

    override fun visitCallInst(inst: CallInst) {
        val args = inst.args.map { tf.getValue(it) }
        val lhv = if (inst.type.isVoid) null else tf.getValue(inst)
        val callTerm = when {
            inst.isStatic -> tf.getCall(inst.method, args)
            else -> {
                val callee = tf.getValue(inst.callee)
                tf.getCall(inst.method, callee, args)
            }
        }

        val predicate = when (lhv) {
            null -> pf.getCall(callTerm, location = inst.location)
            else -> pf.getCall(lhv, callTerm, location = inst.location)
        }

        predicateMap[inst] = predicate
    }

    override fun visitCastInst(inst: CastInst) {
        val lhv = tf.getValue(inst)
        val rhv = tf.getCast(
                inst.type.kexType,
                tf.getValue(inst.operand)
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val lhv = tf.getValue(inst)
        val rhv = tf.getCmp(
                inst.opcode,
                tf.getValue(inst.lhv),
                tf.getValue(inst.rhv)
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val lhv = tf.getValue(inst)
        val field = when {
            inst.hasOwner -> tf.getField(
                    KexReference(inst.type.kexType),
                    tf.getValue(inst.owner),
                    tf.getString(inst.field.name)
            )
            else -> tf.getField(
                    KexReference(inst.type.kexType),
                    inst.field.`class`,
                    tf.getString(inst.field.name)
            )
        }
        val rhv = tf.getFieldLoad(inst.type.kexType, field)

        predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val objectRef = if (inst.isStatic) null else tf.getValue(inst.owner)
        val name = tf.getString(inst.field.name)
        val value = tf.getValue(inst.value)

        val field = when {
            objectRef != null -> tf.getField(KexReference(inst.field.type.kexType), objectRef, name)
            else -> tf.getField(KexReference(inst.field.type.kexType), inst.field.`class`, name)
        }
        predicateMap[inst] = pf.getFieldStore(field, inst.field.type, value, location = inst.location)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val lhv = tf.getValue(inst)
        val rhv = tf.getInstanceOf(
                inst.targetType.kexType,
                tf.getValue(inst.operand)
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val lhv = tf.getValue(inst)
        val dimensions = inst.dimensions.map { tf.getValue(it) }

        predicateMap[inst] = pf.getNewArray(lhv, dimensions, location = inst.location)
    }

    override fun visitNewInst(inst: NewInst) {
        val lhv = tf.getValue(inst)
        predicateMap[inst] = pf.getNew(lhv, location = inst.location)
    }

    override fun visitPhiInst(inst: PhiInst) {
        for ((from, value) in inst.incomings) {
            val lhv = tf.getValue(inst)
            val rhv = tf.getValue(value)
            phiPredicateMap[from to inst] = pf.getEquality(lhv, rhv, location = inst.location)
        }
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        val lhv = tf.getValue(inst)
        val rhv = tf.getUnaryTerm(
                tf.getValue(inst.operand),
                inst.opcode
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val key = tf.getValue(inst.key)
        for ((value, successor) in inst.branches) {
            terminatorPredicateMap[successor to inst] = pf.getEquality(
                    key,
                    tf.getValue(value),
                    PredicateType.Path(),
                    inst.location
            )
        }
        terminatorPredicateMap[inst.default to inst] = pf.getDefaultSwitchPredicate(
                key,
                inst.branches.keys.map { tf.getValue(it) },
                PredicateType.Path(),
                inst.location
        )
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val key = tf.getValue(inst.index)
        val min = inst.min as? IntConstant ?: unreachable { log.error("Unexpected min type in tableSwitchInst") }
        val max = inst.max as? IntConstant ?: unreachable { log.error("Unexpected max type in tableSwitchInst") }
        for ((index, successor) in inst.getBranches().withIndex()) {
            terminatorPredicateMap[successor to inst] = pf.getEquality(
                    key,
                    tf.getInt(min.value + index),
                    PredicateType.Path(),
                    inst.location
            )
        }
        terminatorPredicateMap[inst.getDefault() to inst] = pf.getDefaultSwitchPredicate(
                key,
                (min.value..max.value).map { tf.getInt(it) },
                PredicateType.Path(),
                inst.location
        )
    }

    override fun visitReturnInst(inst: ReturnInst) {
        if (inst.hasReturnValue) {
            val method = inst.parent?.parent ?: unreachable { log.error("Instruction doesn't have parents") }
            val lhv = tf.getReturn(method)
            val rhv = tf.getValue(inst.returnValue)

            predicateMap[inst] = pf.getEquality(lhv, rhv, location = inst.location)
        }
    }

    // ignored instructions
    override fun visitCatchInst(inst: CatchInst) = Unit

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) = Unit
    override fun visitExitMonitorInst(inst: ExitMonitorInst) = Unit
    override fun visitJumpInst(inst: JumpInst) = Unit
    override fun visitThrowInst(inst: ThrowInst) = Unit
}