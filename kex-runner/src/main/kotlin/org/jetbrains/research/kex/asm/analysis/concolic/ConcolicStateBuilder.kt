package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.asm.state.InvalidInstructionError
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.Optimizer
import org.jetbrains.research.kex.state.transformer.TermRenamer
import org.jetbrains.research.kex.state.transformer.collectArguments
import org.jetbrains.research.kex.state.transformer.collectTerms
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.collection.listOf
import org.jetbrains.research.kthelper.collection.stackOf

class UnsupportedInstructionException(val inst: Instruction) : Exception(inst.print())
class InvalidStateException(msg: String) : Exception(msg)

class ConcolicStateBuilder(val cm: ClassManager, val psa: PredicateStateAnalysis) {
    private val types get() = cm.type
    private val stateBuilder = StateBuilder()
    private val callStack = stackOf<MutableMap<Value, Term>>(mutableMapOf())
    private val valueMap get() = callStack.peek()
    private val returnReceivers = stackOf<Term>()
    private val inlinedCalls = stackOf<Method>()
    private var counter = 0
    private var lastCall: Pair<Method, CallParameters>? = null

    fun apply() = Optimizer().apply(stateBuilder.apply())

    data class CallParameters(val receiver: Value?, val mappings: Map<Value, Value>)

    fun enterMethod(method: Method) {
        if (lastCall != null) {
            val params = lastCall!!.second
            // if call params are not null, we should already have value map
            params.receiver?.run { returnReceivers.push(mkNewValue(this)) }
            callStack.push(valueMap.toMutableMap())
            inlinedCalls.push(method)
            for ((arg, value) in params.mappings) {
                valueMap[arg] = mkValue(value)
            }
            lastCall = null
        } else {
            callStack.push(valueMap.toMutableMap())
        }
    }

    fun callMethod(method: Method, parameters: CallParameters) {
        lastCall = method to parameters
    }

    @Suppress("UNUSED_PARAMETER")
    fun exitMethod(method: Method) {
        callStack.pop()
    }

    fun build(block: BasicBlock, entry: BasicBlock?, exit: BasicBlock?) {
        for (inst in block) {
            build(inst, entry, exit)
        }
    }

    fun build(instruction: Instruction, entry: BasicBlock?, exit: BasicBlock?) = when (instruction) {
        is PhiInst -> buildPhiInst(instruction, entry!!)
        is TerminateInst -> buildTerminateInst(instruction, exit)
        else -> buildInst(instruction)
    }

    fun buildInst(instruction: Instruction) = when (instruction) {
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

    fun buildTerminateInst(inst: TerminateInst, next: BasicBlock?) =
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
            }
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

    fun buildPhiInst(inst: PhiInst, entry: BasicBlock) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(
                inst.incomings[entry]
                    ?: throw InvalidStateException("Inst ${inst.print()} predecessors does not correspond to actual predecessor ${entry.name}")
            )
            lhv equality rhv
        }
    }

    fun buildArrayLoadInst(inst: ArrayLoadInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val ref = mkValue(inst.arrayRef)
            val indx = mkValue(inst.index)
            val arrayRef = ref[indx]
            val load = arrayRef.load()

            lhv equality load
        }
    }

    fun buildArrayStoreInst(inst: ArrayStoreInst) {
        stateBuilder += state(inst.location) {
            val ref = mkValue(inst.arrayRef)
            val indx = mkValue(inst.index)
            val arrayRef = ref[indx]
            val value = mkValue(inst.value)

            arrayRef.store(value)
        }
    }

    fun buildBinaryInst(inst: BinaryInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.lhv).apply(types, inst.opcode, mkValue(inst.rhv))

            lhv equality rhv
        }
    }

    fun buildCallInst(inst: CallInst) {
        when (inst.method) {
            inlinedCalls.peek() -> inlinedCalls.pop()
            else -> {
                var fallback: PredicateState = BasicState(listOf {
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
                })
                if (lastCall != null) {
                    val method = inst.method

                    fallback = resolveMethodCall(method) ?: fallback
                    lastCall = null
                    val retTerm = collectTerms(fallback) { it.name.startsWith("<retval>") }.firstOrNull()
                    if (retTerm != null) {
                        fallback += state(inst.location) {
                            mkNewValue(inst) equality retTerm
                        }
                    }
                }
                stateBuilder += fallback
            }
        }
    }

    private fun buildMappings(method: Method, state: PredicateState, callMappings: CallParameters): Map<Term, Term> {
        val (`this`, args) = collectArguments(state)
        val mappings = mutableMapOf<Term, Term>()
        if (!method.isStatic) {
            val actualThis = `this` ?: term { `this`(method.`class`.kexType) }
            val mappedThis = mkValue(callMappings.mappings[cm.value.getThis(method.`class`)]!!)
            mappings += actualThis to mappedThis
        }
        for ((_, argTerm) in args) {
            val valueArg = cm.value.getArgument(argTerm.index, method, argTerm.type.getKfgType(types))
            val mappedArg = mkValue(callMappings.mappings[valueArg]!!)
            mappings += argTerm to mappedArg
        }
        return mappings
    }

    private fun getOverloadedMethod(callee: Value, baseMethod: Method): Method {
        val klass = (callee.type as ClassType).`class`
        return klass.getMethod(baseMethod.name, baseMethod.desc)
    }

    private fun resolveMethodCall(method: Method): PredicateState? {
        if (lastCall == null) return null
        if (method.isEmpty()) return null
        if (method.`class` !is ConcreteClass) return null
        val (callMethod, callMappings) = lastCall!!
        return when {
            method.isStatic -> {
                val builder = psa.builder(method)
                builder.methodState
            }
            method.isConstructor -> {
                val builder = psa.builder(method)
                val state = builder.methodState ?: return null
                val mappings = buildMappings(method, state, callMappings)
                TermRenamer("${++counter}", mappings).apply(state)
            }
            else -> {
                val mappedThis = callMappings.mappings[cm.value.getThis(method.`class`)]!!
                val actualMethod = getOverloadedMethod(mappedThis, callMethod)

                val builder = psa.builder(actualMethod)
                val state = builder.methodState ?: return null
                val mappings = buildMappings(method, state, callMappings)
                TermRenamer("${++counter}", mappings).apply(state)
            }
        }
    }


    fun buildCastInst(inst: CastInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.operand) `as` inst.type.kexType

            lhv equality rhv
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildCatchInst(inst: CatchInst) {}

    fun buildCmpInst(inst: CmpInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.lhv).apply(inst.opcode, mkValue(inst.rhv))

            lhv equality rhv
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildEnterMonitorInst(inst: EnterMonitorInst) {}

    @Suppress("UNUSED_PARAMETER")
    fun buildExitMonitorInst(inst: ExitMonitorInst) {}

    fun buildFieldLoadInst(inst: FieldLoadInst) {
        stateBuilder += state(inst.location) {
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

    fun buildFieldStoreInst(inst: FieldStoreInst) {
        stateBuilder += state(inst.location) {
            val owner = when {
                inst.isStatic -> `class`(inst.field.`class`)
                else -> mkValue(inst.owner)
            }
            val value = mkValue(inst.value)
            val field = owner.field(inst.field.type.kexType, inst.field.name)

            field.store(value)
        }
    }

    fun buildInstanceOfInst(inst: InstanceOfInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.operand) `is` inst.targetType.kexType

            lhv equality rhv
        }
    }

    fun buildNewArrayInst(inst: NewArrayInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val dimensions = inst.dimensions.map { mkValue(it) }

            lhv.new(dimensions)
        }
    }

    fun buildNewInst(inst: NewInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            lhv.new()
        }
    }

    fun buildUnaryInst(inst: UnaryInst) {
        stateBuilder += state(inst.location) {
            val lhv = mkNewValue(inst)
            val rhv = mkValue(inst.operand).apply(inst.opcode)

            lhv equality rhv
        }
    }

    fun buildBranchInst(inst: BranchInst, next: BasicBlock) {
        stateBuilder += path(inst.location) {
            val cond = mkValue(inst.cond)
            when (next) {
                inst.trueSuccessor -> cond equality true
                inst.falseSuccessor -> cond equality false
                else -> throw InvalidStateException("Inst ${inst.print()} successor does not correspond to actual successor ${next.name}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildJumpInst(inst: JumpInst, next: BasicBlock) {}

    fun buildReturnInst(inst: ReturnInst) {
        stateBuilder += state(inst.location) {
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

    fun buildSwitchInst(inst: SwitchInst, next: BasicBlock) {
        val key = mkValue(inst.key)

        when (next) {
            inst.default -> stateBuilder += path(inst.location) { key `!in` inst.branches.keys.map { mkValue(it) } }
            in inst.branches.values -> {
                for ((value, branch) in inst.branches) {
                    if (branch == next) {
                        stateBuilder += path(inst.location) { key equality mkValue(value) }
                    }
                }
            }
            else -> throw InvalidStateException("Inst ${inst.print()} successor does not correspond to actual successor ${next.name}")
        }
    }

    fun buildTableSwitchInst(inst: TableSwitchInst, next: BasicBlock) {
        val key = mkValue(inst.index)
        val min = inst.min as? IntConstant ?: throw InvalidInstructionError("Unexpected min type in tableSwitchInst")
        val max = inst.max as? IntConstant ?: throw InvalidInstructionError("Unexpected max type in tableSwitchInst")

        when (next) {
            inst.default -> stateBuilder += path(inst.location) { key `!in` (min.value..max.value).map { const(it) } }
            in inst.branches -> {
                for ((index, branch) in inst.branches.withIndex()) {
                    if (branch == next) {
                        stateBuilder += path(inst.location) { key equality (min.value + index) }
                    }
                }
            }
            else -> throw InvalidStateException("Inst ${inst.print()} successor does not correspond to actual successor ${next.name}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildThrowInst(inst: ThrowInst) {}

    @Suppress("UNUSED_PARAMETER")
    fun buildUnreachableInst(inst: UnreachableInst) {}
}