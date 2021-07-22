package org.jetbrains.research.kex.asm.state

import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.TermRenamer
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log

class InvalidInstructionError(message: String) : Exception(message)

class PredicateBuilder(override val cm: ClassManager) : MethodVisitor {
    val predicateMap = hashMapOf<Instruction, Predicate>()
    val phiPredicateMap = hashMapOf<Pair<BasicBlock, Instruction>, Predicate>()
    val terminatorPredicateMap = hashMapOf<Pair<BasicBlock, TerminateInst>, Predicate>()

    override fun cleanup() {
        predicateMap.clear()
        phiPredicateMap.clear()
        terminatorPredicateMap.clear()
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val ref = value(inst.arrayRef)
            val index = value(inst.index)
            val arrayRef = ref[index]
            val load = arrayRef.load()

            lhv equality load
        }
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        predicateMap[inst] = state(inst.location) {
            val ref = value(inst.arrayRef)
            val index = value(inst.index)
            val arrayRef = ref[index]
            val value = value(inst.value)

            arrayRef.store(value)
        }
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val rhv = value(inst.lhv).apply(types, inst.opcode, value(inst.rhv))

            lhv equality rhv
        }
    }

    override fun visitBranchInst(inst: BranchInst) {
        val cond = term { value(inst.cond) }
        terminatorPredicateMap[inst.trueSuccessor to inst] = path(inst.location) { cond equality true }
        terminatorPredicateMap[inst.falseSuccessor to inst] = path(inst.location) { cond equality false }
    }

    override fun visitCallInst(inst: CallInst) {
        predicateMap[inst] = state(inst.location) {
            val args = inst.args.map { value(it) }
            val callee = when {
                inst.isStatic -> `class`(inst.method.klass)
                else -> value(inst.callee)
            }
            val callTerm = callee.call(inst.method, args)

            when {
                inst.isNameDefined -> value(inst).call(callTerm)
                else -> call(callTerm)
            }
        }
    }

    override fun visitCastInst(inst: CastInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val rhv = value(inst.operand) `as` inst.type.kexType

            lhv equality rhv
        }
    }

    override fun visitCmpInst(inst: CmpInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val rhv = value(inst.lhv).apply(inst.opcode, value(inst.rhv))

            lhv equality rhv
        }
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val owner = when {
                inst.isStatic -> `class`(inst.field.klass)
                else -> value(inst.owner)
            }
            val field = owner.field(inst.type.kexType, inst.field.name)
            val rhv = field.load()

            lhv equality rhv
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        predicateMap[inst] = state(inst.location) {
            val owner = when {
                inst.isStatic -> `class`(inst.field.klass)
                else -> value(inst.owner)
            }
            val value = value(inst.value)
            val field = owner.field(inst.field.type.kexType, inst.field.name)

            field.store(value)
        }
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val rhv = value(inst.operand) `is` inst.targetType.kexType

            lhv equality rhv
        }
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)

            val lambdaBases = inst.bootstrapMethodArgs.filterIsInstance<Handle>()
            ktassert(lambdaBases.size == 1) { log.error("Unknown number of bases of ${inst.print()}") }
            val lambdaBase = lambdaBases.first()

            val argParameters = lambdaBase.method.argTypes.withIndex().map { arg(it.value.kexType, it.index) }
            val lambdaParameters = lambdaBase.method.argTypes.withIndex().map { (index, type) ->
                term { value(type.kexType, "labmda_${lambdaBase.method.name}_$index") }
            }

            val psa = PredicateStateAnalysis(cm)
            val lambdaBody = psa.builder(lambdaBase.method).methodState
                ?: return log.error("Could not process ${inst.print()}")

            lhv equality lambda(inst.type.kexType, lambdaParameters) {
                TermRenamer(".labmda.${lambdaBase.method.name}", argParameters.zip(lambdaParameters).toMap())
                    .apply(lambdaBody)
            }
        }
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val dimensions = inst.dimensions.map { value(it) }

            lhv.new(dimensions)
        }
    }

    override fun visitNewInst(inst: NewInst) {
        predicateMap[inst] = state(inst.location) { value(inst).new() }
    }

    override fun visitPhiInst(inst: PhiInst) {
        for ((from, value) in inst.incomings) {
            phiPredicateMap[from to inst] = state(inst.location) {
                val lhv = value(inst)
                val rhv = value(value)

                lhv equality rhv
            }
        }
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        predicateMap[inst] = state(inst.location) {
            val lhv = value(inst)
            val rhv = value(inst.operand).apply(inst.opcode)

            lhv equality rhv
        }
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val key = term { value(inst.key) }
        for ((value, successor) in inst.branches) {
            terminatorPredicateMap[successor to inst] = path(inst.location) { key equality value(value) }
        }
        terminatorPredicateMap[inst.default to inst] =
            path(inst.location) { key `!in` inst.branches.keys.map { value(it) } }
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val key = term { value(inst.index) }
        val min = inst.min as? IntConstant ?: throw InvalidInstructionError("Unexpected min type in tableSwitchInst")
        val max = inst.max as? IntConstant ?: throw InvalidInstructionError("Unexpected max type in tableSwitchInst")
        for ((index, successor) in inst.branches.withIndex()) {
            terminatorPredicateMap[successor to inst] = path(inst.location) { key equality (min.value + index) }
        }
        terminatorPredicateMap[inst.default to inst] =
            path(inst.location) { key `!in` (min.value..max.value).map { const(it) } }
    }

    override fun visitReturnInst(inst: ReturnInst) {
        if (inst.hasReturnValue) {
            predicateMap[inst] = state(inst.location) {
                val method = inst.parent.parent
                val lhv = `return`(method)
                val rhv = value(inst.returnValue)

                lhv equality rhv
            }
        }
    }

    // ignored instructions
    override fun visitCatchInst(inst: CatchInst) = Unit

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) = Unit
    override fun visitExitMonitorInst(inst: ExitMonitorInst) = Unit
    override fun visitJumpInst(inst: JumpInst) = Unit
    override fun visitThrowInst(inst: ThrowInst) = Unit
}