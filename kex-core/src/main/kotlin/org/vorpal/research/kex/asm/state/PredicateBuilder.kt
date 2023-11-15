package org.vorpal.research.kex.asm.state

import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.TermRenamer
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.Handle
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TerminateInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnknownValueInst
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.zipTo
import org.vorpal.research.kthelper.logging.log

class InvalidInstructionError(message: String) : Exception(message)

class PredicateBuilder(override val cm: ClassManager) : MethodVisitor {
    private val innerTermMap = hashMapOf<Value, Term>()
    private val innerPredicateMap = hashMapOf<Instruction, Predicate>()
    private val innerPhiPredicateMap = hashMapOf<Pair<BasicBlock, Instruction>, Predicate>()
    private val innerTerminatorPredicateMap = hashMapOf<Pair<BasicBlock, TerminateInst>, MutableSet<Predicate>>()

    @Suppress("unused")
    val termMap: Map<Value, Term> get() = innerTermMap
    val predicateMap: Map<Instruction, Predicate> get() = innerPredicateMap
    val phiPredicateMap: Map<Pair<BasicBlock, Instruction>, Predicate> get() = innerPhiPredicateMap
    val terminatorPredicateMap: Map<Pair<BasicBlock, TerminateInst>, MutableSet<Predicate>> get() = innerTerminatorPredicateMap

    override fun cleanup() {
        innerTermMap.clear()
        innerPredicateMap.clear()
        innerPhiPredicateMap.clear()
        innerTerminatorPredicateMap.clear()
    }

    private fun mkValue(value: Value) = innerTermMap.getOrElse(value) { term { value(value) } }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val ref = mkValue(inst.arrayRef)
            val index = mkValue(inst.index)
            val arrayRef = ref[index]
            val load = arrayRef.load()

            lhv equality load
        }
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val ref = mkValue(inst.arrayRef)
            val index = mkValue(inst.index)
            val arrayRef = ref[index]
            val value = mkValue(inst.value)

            arrayRef.store(value)
        }
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val rhv = mkValue(inst.lhv).apply(types, inst.opcode, mkValue(inst.rhv))

            lhv equality rhv
        }
    }

    override fun visitBranchInst(inst: BranchInst) {
        val cond = term { mkValue(inst.cond) }
        innerTerminatorPredicateMap.getOrPut(inst.trueSuccessor to inst, ::hashSetOf).add(
            path(inst.location) { cond equality true }
        )
        innerTerminatorPredicateMap.getOrPut(inst.falseSuccessor to inst, ::hashSetOf).add(
            path(inst.location) { cond equality false }
        )
    }

    override fun visitCallInst(inst: CallInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val args = inst.args.map { mkValue(it) }
            val callee = when {
                inst.isStatic -> staticRef(inst.method.klass)
                else -> mkValue(inst.callee)
            }
            val callTerm = callee.call(inst.method, args)

            when {
                inst.isNameDefined -> mkValue(inst).call(callTerm)
                else -> call(callTerm)
            }
        }
    }

    override fun visitCastInst(inst: CastInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val rhv = mkValue(inst.operand) `as` inst.type.kexType

            lhv equality rhv
        }
    }

    override fun visitCmpInst(inst: CmpInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val rhv = mkValue(inst.lhv).apply(inst.opcode, mkValue(inst.rhv))

            lhv equality rhv
        }
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val owner = when {
                inst.isStatic -> staticRef(inst.field.klass)
                else -> mkValue(inst.owner)
            }
            val field = owner.field(inst.type.kexType, inst.field.name)
            val rhv = field.load()

            lhv equality rhv
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val owner = when {
                inst.isStatic -> staticRef(inst.field.klass)
                else -> mkValue(inst.owner)
            }
            val value = mkValue(inst.value)
            val field = owner.field(inst.field.type.kexType, inst.field.name)

            field.store(value)
        }
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val rhv = mkValue(inst.operand) `is` inst.targetType.kexType

            lhv equality rhv
        }
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        val lambdaBases = inst.bootstrapMethodArgs.filterIsInstance<Handle>()
        ktassert(lambdaBases.size == 1) { log.error("Unknown number of bases of ${inst.print()}") }
        val lambdaBase = lambdaBases.first()

        val argParameters = lambdaBase.method.argTypes.mapIndexed { index, type -> term { arg(type.kexType, index) } }
        val lambdaParameters = lambdaBase.method.argTypes.mapIndexed { index, type ->
            term { value(type.kexType, "labmda_${lambdaBase.method.name}_$index") }
        }
        val mapping = argParameters.zipTo(lambdaParameters, mutableMapOf())
        val `this` = term { `this`(lambdaBase.method.klass.kexType) }
        mapping[`this`] = `this`

        val expr = lambdaBase.method.asTermExpr()
            ?: return log.error("Could not process ${inst.print()}")

        innerTermMap[inst] = term {
            lambda(inst.type.kexType, lambdaParameters) {
                TermRenamer("labmda.${lambdaBase.method.name}", mapping)
                    .transform(expr)
            }
        }
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val dimensions = inst.dimensions.map { mkValue(it) }

            lhv.new(dimensions)
        }
    }

    override fun visitNewInst(inst: NewInst) {
        innerPredicateMap[inst] = state(inst.location) { mkValue(inst).new() }
    }

    override fun visitPhiInst(inst: PhiInst) {
        for ((from, value) in inst.incomings) {
            innerPhiPredicateMap[from to inst] = state(inst.location) {
                val lhv = mkValue(inst)
                val rhv = mkValue(value)

                lhv equality rhv
            }
        }
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val lhv = mkValue(inst)
            val rhv = mkValue(inst.operand).apply(inst.opcode)

            lhv equality rhv
        }
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val key = term { mkValue(inst.key) }
        for ((value, successor) in inst.branches) {
            innerTerminatorPredicateMap.getOrPut(successor to inst, ::hashSetOf).add(
                path(inst.location) { key equality mkValue(value) }
            )
        }
        innerTerminatorPredicateMap.getOrPut(inst.default to inst, ::hashSetOf).add(
            path(inst.location) { key `!in` inst.branches.keys.map { mkValue(it) } }
        )
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val key = term { mkValue(inst.index) }
        val min = inst.min as? IntConstant ?: throw InvalidInstructionError("Unexpected min type in tableSwitchInst")
        val max = inst.max as? IntConstant ?: throw InvalidInstructionError("Unexpected max type in tableSwitchInst")
        for ((index, successor) in inst.branches.withIndex()) {
            innerTerminatorPredicateMap.getOrPut(successor to inst, ::hashSetOf).add(
                path(inst.location) { key equality (min.value + index) }
            )
        }
        innerTerminatorPredicateMap.getOrPut(inst.default to inst, ::hashSetOf).add(
            path(inst.location) { key `!in` (min.value..max.value).map { const(it) } }
        )
    }

    override fun visitReturnInst(inst: ReturnInst) {
        if (inst.hasReturnValue) {
            innerPredicateMap[inst] = state(inst.location) {
                val method = inst.parent.method
                val lhv = `return`(method)
                val rhv = mkValue(inst.returnValue)

                lhv equality rhv
            }
        }
    }

    override fun visitUnknownValueInst(inst: UnknownValueInst) {
        innerPredicateMap[inst] = state(inst.location) {
            val i = mkValue(inst).apply(CmpOpcode.CMPG, mkValue(values.getInt(0)))
            i equality true
        }
    }

    // ignored instructions
    override fun visitCatchInst(inst: CatchInst) = Unit

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) = Unit
    override fun visitExitMonitorInst(inst: ExitMonitorInst) = Unit
    override fun visitJumpInst(inst: JumpInst) = Unit
    override fun visitThrowInst(inst: ThrowInst) = Unit
}
