package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor

class PredicateBuilder(method: Method) : MethodVisitor(method), Loggable {
    val tf = TermFactory
    val pf = PredicateFactory
    val predicateMap = mutableMapOf<Instruction, Predicate>()
    val phiPredicateMap = mutableMapOf<Pair<BasicBlock, Instruction>, Predicate>()
    val terminatorPredicateMap = mutableMapOf<Pair<BasicBlock, TerminateInst>, Predicate>()

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = tf.getArrayLoad(
                tf.getValueTerm(inst.getArrayRef()),
                tf.getValueTerm(inst.getIndex())
        )

        predicateMap[inst] = pf.getLoad(lhv, rhv)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val lhv = tf.getArrayLoad(
                tf.getValueTerm(inst.getArrayRef()),
                tf.getValueTerm(inst.getIndex())
        )
        val rhv = tf.getValueTerm(inst.getValue())

        predicateMap[inst] = pf.getStore(lhv, rhv)
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = tf.getBinary(
                tf.getValueTerm(inst.getLhv()),
                tf.getValueTerm(inst.getRhv()),
                inst.opcode
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitBranchInst(inst: BranchInst) {
        val cond = tf.getValueTerm(inst.getCond())
        terminatorPredicateMap[inst.getTrueSuccessor() to inst] = pf.getBoolean(
                cond,
                tf.getTrue()
        )
        terminatorPredicateMap[inst.getFalseSuccessor() to inst] = pf.getBoolean(
                cond,
                tf.getFalse()
        )
    }

    override fun visitCallInst(inst: CallInst) {
        val args = inst.getArgs().map { tf.getValueTerm(it) }
        val lhv = if (inst.type.isVoid()) null else tf.getValueTerm(inst)
        val callTerm = if (inst.isStatic) {
            tf.getCall(inst.method, args)
        } else {
            val callee = tf.getValueTerm(inst.getCallee()!!)
            tf.getCall(inst.method, callee, args)
        }

        val predicate = if (lhv == null) pf.getCall(callTerm) else pf.getCall(lhv, callTerm)

        predicateMap[inst] = predicate
    }

    override fun visitCastInst(inst: CastInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = tf.getCast(
                inst.type,
                tf.getValueTerm(inst.getOperand())
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = tf.getCmp(
                tf.getValueTerm(inst.getLhv()),
                tf.getValueTerm(inst.getRhv()),
                inst.opcode
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = if (inst.hasOwner()) {
            tf.getFieldLoad(
                    inst.type,
                    tf.getValueTerm(inst.getOwner()!!),
                    tf.getString(inst.field.name)
            )
        } else {
            tf.getFieldLoad(
                    inst.type,
                    inst.field.`class`,
                    tf.getString(inst.field.name)
            )
        }

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val lhv = if (inst.hasOwner()) {
            tf.getFieldLoad(
                    inst.type,
                    tf.getValueTerm(inst.getOwner()!!),
                    tf.getString(inst.field.name)
            )
        } else {
            tf.getFieldLoad(
                    inst.type,
                    inst.field.`class`,
                    tf.getString(inst.field.name)
            )
        }
        val rhv = tf.getValueTerm(inst.getValue())

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = tf.getInstanceOf(
                inst.targetType,
                tf.getValueTerm(inst.getOperand())
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitMultiNewArrayInst(inst: MultiNewArrayInst) {
        val lhv = tf.getValueTerm(inst)
        val dimensions = inst.getDimensions().map { tf.getValueTerm(it) }

        predicateMap[inst] = pf.getMultipleNewArray(lhv, dimensions)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val lhv = tf.getValueTerm(inst)
        val numElements = tf.getValueTerm(inst.getCount())

        predicateMap[inst] = pf.getNewArray(lhv, numElements)
    }

    override fun visitNewInst(inst: NewInst) {
        val lhv = tf.getValueTerm(inst)
        predicateMap[inst] = pf.getNew(lhv)
    }

    override fun visitPhiInst(inst: PhiInst) {
        for ((from, value) in inst.getIncomings()) {
            val lhv = tf.getValueTerm(inst)
            val rhv = tf.getValueTerm(value)
            phiPredicateMap[from to inst] = pf.getEquality(lhv, rhv)
        }
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        val lhv = tf.getValueTerm(inst)
        val rhv = tf.getUnaryTerm(
                tf.getValueTerm(inst.getOperand()),
                inst.opcode
        )

        predicateMap[inst] = pf.getEquality(lhv, rhv)
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val key = tf.getValueTerm(inst.getKey())
        for ((value, successor) in inst.getBranches()) {
            terminatorPredicateMap[successor to inst] = pf.getEquality(
                    key,
                    tf.getValueTerm(value),
                    PredicateType.Path()
            )
        }
        terminatorPredicateMap[inst.getDefault() to inst] = pf.getDefaultSwitchPredicate(
                key,
                inst.getBranches().keys.map { tf.getValueTerm(it) },
                PredicateType.Path()
        )
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val key = tf.getValueTerm(inst.getIndex())
        val min = inst.getMin() as? IntConstant ?: unreachable({ log.error("Unexpected min type in tableSwitchInst") })
        val max = inst.getMax() as? IntConstant ?: unreachable({ log.error("Unexpected max type in tableSwitchInst") })
        for ((index, successor) in inst.getBranches().withIndex()) {
            terminatorPredicateMap[successor to inst] = pf.getEquality(
                    key,
                    tf.getInt(min.value + index),
                    PredicateType.Path()
            )
        }
        terminatorPredicateMap[inst.getDefault() to inst] = pf.getDefaultSwitchPredicate(
                key,
                (min.value..max.value).map { tf.getInt(it) },
                PredicateType.Path()
        )
    }

    override fun visitReturnInst(inst: ReturnInst) {
        if (inst.hasReturnValue()) {
            val lhv = tf.getReturn(method)
            val rhv = tf.getValueTerm(inst.getReturnValue())

            predicateMap[inst] = pf.getEquality(lhv, rhv)
        }
    }

    // ignored instructions
    override fun visitCatchInst(inst: CatchInst) {}
    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {}
    override fun visitExitMonitorInst(inst: ExitMonitorInst) {}
    override fun visitJumpInst(inst: JumpInst) {}
    override fun visitThrowInst(inst: ThrowInst) {}
}