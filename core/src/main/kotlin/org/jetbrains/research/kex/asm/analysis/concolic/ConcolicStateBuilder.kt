package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.asm.state.InvalidInstructionError
import org.jetbrains.research.kex.collections.stackOf
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.util.buildList
import org.jetbrains.research.kex.util.listOf
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*

class UnsupportedInstructionException(val inst: Instruction) : Exception(inst.print())
class InvalidStateException(msg: String) : Exception(msg)

class ConcolicStateBuilder(val cm: ClassManager) {
    private val types get() = cm.type
    private val stateBuilder = StateBuilder()
    private val callStack = stackOf<MutableMap<Value, Term>>(mutableMapOf())
    private val valueMap get() = callStack.peek()
    private val returnReceivers = stackOf<Term>()
    private val inlinedCalls = stackOf<Method>()
    private var counter = 0

    fun apply() = stateBuilder.apply()

    data class CallParameters(val receiver: Value?, val mappings: Map<Value, Value>)

    fun enterMethod(method: Method, params: CallParameters? = null) {
        if (params != null) {
            // if call params are not null, we should already have value map
            params.receiver?.run { returnReceivers.push(mkNewValue(this)) }
            callStack.push(valueMap.toMutableMap())
            inlinedCalls.push(method)
            for ((arg, value) in params.mappings) {
                valueMap[arg] = mkValue(value)
            }
//            callStack.push(tempMap)
        } else {
            callStack.push(valueMap.toMutableMap())
//            callStack.push(mutableMapOf())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun exitMethod(method: Method) {
        callStack.pop()
    }

    fun build(block: BasicBlock, entry: BasicBlock?, exit: BasicBlock?) {
        for (inst in block) {
            for (pred in build(inst, entry, exit))
                stateBuilder += pred
        }
    }

    fun build(instruction: Instruction, entry: BasicBlock?, exit: BasicBlock?) = when (instruction) {
        is PhiInst -> buildPhiInst(instruction, entry!!)
        is TerminateInst -> buildTerminateInst(instruction, exit)
        else -> buildInst(instruction)
    }

    fun buildInst(instruction: Instruction): List<Predicate> = when (instruction) {
        is ArrayLoadInst -> buildArrayLoadInst(instruction)
        is ArrayStoreInst -> buildArrayStoreInst(instruction)
        is BinaryInst -> buildBinaryInst(instruction)
        is CallInst -> buildCallInst(instruction)
        is CastInst -> buildCastInst(instruction)
        is CatchInst -> buildCatchInst(instruction)
        is CmpInst -> buildCmpInst(instruction)
        is EnterMonitorInst -> buildEnterMonitorInst(instruction)
        is ExitMonitorInst -> buildExitMonitorInst(instruction)
        is FieldLoadInst -> buildFieldLoadInst(instruction)
        is FieldStoreInst -> buildFieldStoreInst(instruction)
        is InstanceOfInst -> buildInstanceOfInst(instruction)
        is NewArrayInst -> buildNewArrayInst(instruction)
        is NewInst -> buildNewInst(instruction)
        is UnaryInst -> buildUnaryInst(instruction)
        else -> throw UnsupportedInstructionException(instruction)
    }

    fun buildTerminateInst(inst: TerminateInst, next: BasicBlock?): List<Predicate> =
            when (inst) {
                is ReturnInst -> buildReturnInst(inst)
                is ThrowInst -> buildThrowInst(inst)
                is UnreachableInst -> buildUnreachableInst(inst)
                else -> next?.run {
                    when (inst) {
                        is BranchInst -> buildBranchInst(inst, next)
                        is JumpInst -> buildJumpInst(inst, next)
                        is SwitchInst -> buildSwitchInst(inst, next)
                        is TableSwitchInst -> buildTableSwitchInst(inst, next)
                        else -> throw UnsupportedInstructionException(inst)
                    }
                } ?: emptyList()
            }

    private fun newValue(value: Value) = term {
        when (value) {
            is Argument -> arg(value)
            is Constant -> const(value)
            is ThisRef -> `this`(value.type.kexType)
            else -> tf.getValue(value.type.kexType, "$value.${++counter}")
        }
    }

    private fun mkValue(value: Value) = valueMap.getOrPut(value) { newValue(value) }

    private fun mkNewValue(value: Value): Term {
        val v = newValue(value)
        valueMap[value] = v
        return v
    }

    fun buildPhiInst(inst: PhiInst, entry: BasicBlock): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.incomings[entry]
                    ?: throw InvalidStateException("Inst ${inst.print()} predecessors does not correspond to actual predecessor ${entry.name}")
            )
            lhv equality rhv
        }
    }

    fun buildArrayLoadInst(inst: ArrayLoadInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val ref = mkValue(inst.arrayRef)
            val indx = mkValue(inst.index)
            val arrayRef = ref[indx]
            val load = arrayRef.load()

            lhv equality load
        }
    }

    fun buildArrayStoreInst(inst: ArrayStoreInst): List<Predicate> = listOf {
        state(inst.location) {
            val ref = mkValue(inst.arrayRef)
            val indx = mkValue(inst.index)
            val arrayRef = ref[indx]
            val value = mkValue(inst.value)

            arrayRef.store(value)
        }
    }

    fun buildBinaryInst(inst: BinaryInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.lhv).apply(types, inst.opcode, mkValue(inst.rhv))

            lhv equality rhv
        }
    }

    fun buildCallInst(inst: CallInst): List<Predicate> = when {
        inst.method == inlinedCalls.peek() -> {
            inlinedCalls.pop()
            emptyList()
        }
        else -> listOf {
            state(inst.location) {
                val args = inst.args.map { mkValue(it) }
                val callee = when {
                    inst.isStatic -> `class`(inst.method.`class`)
                    else -> mkValue(inst.callee)
                }
                val callTerm = callee.call(inst.method, args)

                when {
                    inst.isNameDefined -> mkNewValue(inst).call(callTerm)
                    else -> call(callTerm)
                }
            }
        }
    }


    fun buildCastInst(inst: CastInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.operand) `as` inst.type.kexType

            lhv equality rhv
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildCatchInst(inst: CatchInst): List<Predicate> = emptyList()

    fun buildCmpInst(inst: CmpInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.lhv).apply(inst.opcode, mkValue(inst.rhv))

            lhv equality rhv
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildEnterMonitorInst(inst: EnterMonitorInst): List<Predicate> = emptyList()

    @Suppress("UNUSED_PARAMETER")
    fun buildExitMonitorInst(inst: ExitMonitorInst): List<Predicate> = emptyList()

    fun buildFieldLoadInst(inst: FieldLoadInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val owner = when {
                inst.isStatic -> `class`(inst.field.`class`)
                else -> mkValue(inst.owner)
            }
            val field = owner.field(inst.type.kexType, inst.field.name)
            val rhv = field.load()

            lhv equality rhv
        }
    }

    fun buildFieldStoreInst(inst: FieldStoreInst): List<Predicate> = listOf {
        state(inst.location) {
            val owner = when {
                inst.isStatic -> `class`(inst.field.`class`)
                else -> mkValue(inst.owner)
            }
            val value = mkValue(inst.value)
            val field = owner.field(inst.field.type.kexType, inst.field.name)

            field.store(value)
        }
    }

    fun buildInstanceOfInst(inst: InstanceOfInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.operand) `is` inst.targetType.kexType

            lhv equality rhv
        }
    }

    fun buildNewArrayInst(inst: NewArrayInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val dimensions = inst.dimensions.map { mkValue(it) }

            lhv.new(dimensions)
        }
    }

    fun buildNewInst(inst: NewInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            lhv.new()
        }
    }

    fun buildUnaryInst(inst: UnaryInst): List<Predicate> = listOf {
        state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.operand).apply(inst.opcode)

            lhv equality rhv
        }
    }

    fun buildBranchInst(inst: BranchInst, next: BasicBlock): List<Predicate> = listOf {
        path(inst.location) {
            val cond = mkValue(inst.cond)
            when (next) {
                inst.trueSuccessor -> cond equality true
                inst.falseSuccessor -> cond equality false
                else -> throw InvalidStateException("Inst ${inst.print()} successor does not correspond to actual successor ${next.name}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildJumpInst(inst: JumpInst, next: BasicBlock): List<Predicate> = emptyList()

    fun buildReturnInst(inst: ReturnInst): List<Predicate> = listOf {
        state(inst.location) {
            when {
                !inst.hasReturnValue -> const(true) equality true
                returnReceivers.isEmpty() -> const(true) equality true
                else -> {
                    val retval = returnReceivers.pop()
                    val value = mkValue(inst.returnValue)
                    retval equality value
                }
            }
        }
    }

    fun buildSwitchInst(inst: SwitchInst, next: BasicBlock): List<Predicate> = buildList {
        val key = mkValue(inst.key)

        when (next) {
            inst.default -> +path(inst.location) { key `!in` inst.branches.keys.map { mkValue(it) } }
            in inst.branches.values -> {
                for ((value, branch) in inst.branches) {
                    if (branch == next) {
                        +path(inst.location) { key equality mkValue(value) }
                    }
                }
            }
            else -> throw InvalidStateException("Inst ${inst.print()} successor does not correspond to actual successor ${next.name}")
        }
    }

    fun buildTableSwitchInst(inst: TableSwitchInst, next: BasicBlock): List<Predicate> = buildList {
        val key = mkValue(inst.index)
        val min = inst.min as? IntConstant ?: throw InvalidInstructionError("Unexpected min type in tableSwitchInst")
        val max = inst.max as? IntConstant ?: throw InvalidInstructionError("Unexpected max type in tableSwitchInst")

        when (next) {
            inst.default -> +path(inst.location) { key `!in` (min.value..max.value).map { const(it) } }
            in inst.branches -> {
                for ((index, branch) in inst.branches.withIndex()) {
                    if (branch == next) {
                        +path(inst.location) { key equality (min.value + index) }
                    }
                }
            }
            else -> throw InvalidStateException("Inst ${inst.print()} successor does not correspond to actual successor ${next.name}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildThrowInst(inst: ThrowInst): List<Predicate> = emptyList()
    @Suppress("UNUSED_PARAMETER")
    fun buildUnreachableInst(inst: UnreachableInst): List<Predicate> = emptyList()
}