package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermBuilder
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.analysis.LoopManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.VoidType
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log

class TermExpressionBuilder(override val cm: ClassManager) : TermBuilder(), MethodVisitor {
    private val termMap = hashMapOf<Value, Term>()
    private val path = hashMapOf<BasicBlock, Term>()
    lateinit var currentCond: Term
    var returnExpr: Term? = null
        private set

    override fun cleanup() {
        termMap.clear()
    }

    val Value.term get() = termMap.getOrPut(this) { value(this) }
    val BasicBlock.condition get() = path.getOrDefault(this, const(true))

    override fun visit(method: Method) {
        val queue = queueOf(method.entry)
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

    override fun visitJumpInst(inst: JumpInst) {}

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
    if (LoopManager.getMethodLoopInfo(this).isNotEmpty()) {
        log.warn("Could not convert method with loops into term expr")
        return null
    }
    if (!hasBody) return null
    if (returnType is VoidType) return null

    val teb = TermExpressionBuilder(cm)
    teb.visit(this)
    return teb.returnExpr
}