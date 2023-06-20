package org.vorpal.research.kex.asm.state

import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.TermBuilder
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnreachableInst
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.VoidType
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.logging.log

class TermExpressionBuilder(override val cm: ClassManager) : TermBuilder, MethodVisitor {
    private val termMap = mutableMapOf<Value, Term>()
    private val path = mutableMapOf<BasicBlock, Term>()
    private lateinit var currentCond: Term
    var returnExpr: Term? = null
        private set

    override fun cleanup() {
        termMap.clear()
    }

    val Value.term get() = termMap.getOrPut(this) { value(this) }
    val BasicBlock.condition get() = path.getOrDefault(this, const(true))

    override fun visit(method: Method) {
        val queue = queueOf(method.body.entry)
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            currentCond = current.condition
            visitBasicBlock(current)
            queue.addAll(current.successors)
        }
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        termMap[inst] = inst.arrayRef.term[inst.index.term].load()
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        log.warn("Array stores are not supported in expressions")
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        termMap[inst] = inst.lhv.term.apply(inst.type.kexType, inst.opcode, inst.rhv.term)
    }

    override fun visitBranchInst(inst: BranchInst) {
        path[inst.trueSuccessor] = currentCond and inst.cond.term
        path[inst.falseSuccessor] = currentCond and inst.cond.term.not()
    }

    override fun visitCallInst(inst: CallInst) {
        val args = inst.args.map { it.term }
        val callee = when {
            inst.isStatic -> staticRef(inst.method.klass)
            else -> inst.callee.term
        }
        val callTerm = callee.call(inst.method, args)

        if (inst.isNameDefined) termMap[inst] = callTerm
    }

    override fun visitCastInst(inst: CastInst) {
        termMap[inst] = inst.operand.term `as` inst.type.kexType
    }

    override fun visitCatchInst(inst: CatchInst) {}

    override fun visitCmpInst(inst: CmpInst) {
        termMap[inst] = inst.lhv.term.apply(inst.opcode, inst.rhv.term)
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {}
    override fun visitExitMonitorInst(inst: ExitMonitorInst) {}

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        termMap[inst] = when {
            inst.isStatic -> staticRef(inst.field.klass)
            else -> inst.owner.term
        }.field(inst.type.kexType, inst.field.name).load()
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        log.warn("Field stores are not supported in expressions")
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        termMap[inst] = inst.operand.term `is` inst.targetType.kexType
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        log.warn("Invoke dynamic is not supported in expressions")
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        log.warn("New arrays are not supported in expressions")
    }

    override fun visitNewInst(inst: NewInst) {
        log.warn("New objects are not supported in expressions")
    }

    private fun mkIte(
        type: Type,
        index: Int,
        incomings: List<Pair<BasicBlock, Value>>,
        default: Term): Term = when {
            index < incomings.size -> ite(
                type.kexType,
                incomings[index].first.condition,
                incomings[index].second.term,
                mkIte(type, index + 1, incomings, default)
            )
            else -> default
        }

    override fun visitPhiInst(inst: PhiInst) {
        termMap[inst] = mkIte(inst.type, 0, inst.incomings.toList(), default(inst.type.kexType))
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        termMap[inst] = inst.operand.term.apply(inst.opcode)
    }

    override fun visitJumpInst(inst: JumpInst) {
        path[inst.successor] = currentCond
    }

    override fun visitReturnInst(inst: ReturnInst) {
        if (inst.hasReturnValue) {
            returnExpr = inst.returnValue.term
        }
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val key = inst.key.term
        var res: Term = const(true)
        for ((cond, block) in inst.branches) {
            path[block] = currentCond and (key eq cond.term)
            res = res and (key neq cond.term)
        }
        path[inst.default] = currentCond and res
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val key = inst.index.term
        var res: Term = const(true)
        for (index in inst.range) {
            val listIndex = index - inst.range.first
            path[inst.branches[listIndex]] = currentCond and (key eq index)
            res = res and (key neq index)
        }
        path[inst.default] = currentCond and res
    }

    override fun visitThrowInst(inst: ThrowInst) {}
    override fun visitUnreachableInst(inst: UnreachableInst) {}
}

fun Method.asTermExpr(): Term? {
    if (this.hasLoops) {
        log.warn("Could not convert method with loops into term expr")
        return null
    }
    if (!hasBody) return null
    if (returnType is VoidType) return null

    val teb = TermExpressionBuilder(cm)
    teb.visit(this)
    return teb.returnExpr
}
