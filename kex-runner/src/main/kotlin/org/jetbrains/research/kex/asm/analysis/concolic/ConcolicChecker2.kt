package org.jetbrains.research.kex.asm.analysis.concolic

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.reanimator.ParameterGenerator
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.ReflectionReanimator
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.DefaultSwitchPredicate
import org.jetbrains.research.kex.state.predicate.EqualityPredicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.RandomSymbolicTracingRunner
import org.jetbrains.research.kex.trace.runner.SymbolicTracingRunner
import org.jetbrains.research.kex.trace.symbolic.*
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.instruction.BranchInst
import org.jetbrains.research.kfg.ir.value.instruction.SwitchInst
import org.jetbrains.research.kfg.ir.value.instruction.TableSwitchInst
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.dequeOf
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull

class ConcolicChecker2(val ctx: ExecutionContext, val traceManager: TraceManager<InstructionTrace>) : MethodVisitor {
    private val paths = mutableSetOf<PathCondition>()
    private val candidates = mutableSetOf<PathCondition>()
    lateinit var generator: ParameterGenerator
        protected set

    override val cm: ClassManager
        get() = ctx.cm

    override fun cleanup() {
        paths.clear()
    }

    override fun visit(method: Method) {
        if (method.isStaticInitializer || !method.hasBody) return

        log.debug("Checking method $method")
        log.debug(method.print())

        initializeGenerator(method)
        processMethod(method)
    }

    private fun getRandomTrace(method: Method): SymbolicState? = tryOrNull {
        RandomSymbolicTracingRunner(ctx, method).run()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun initializeGenerator(method: Method) {
        generator = ReflectionReanimator(ctx, PredicateStateAnalysis(cm))
    }

    private fun collectTrace(method: Method, parameters: Parameters<Any?>): SymbolicState? {
        val runner = SymbolicTracingRunner(ctx, method, parameters)
        return runner.run()
    }

    private fun Clause.reversed(): Clause? = when (instruction) {
        is BranchInst -> {
            val (cond, value) = with(predicate as EqualityPredicate) {
                lhv to (rhv as ConstBoolTerm).value
            }
            val reversed = Clause(instruction, path(instruction.location) {
                cond equality !value
            })
            if (paths.any { reversed in it }) null
            else reversed
        }
        is SwitchInst -> when (predicate) {
            is DefaultSwitchPredicate -> {
                val cond = predicate.cond
                val candidates = instruction.branches.keys.map { (it as IntConstant).value }.toSet()
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result
            }
            is EqualityPredicate -> {
                val (cond, value) = predicate.lhv to (predicate.rhv as ConstIntTerm).value
                val candidates = instruction.branches.keys.map { (it as IntConstant).value }.toSet() - value
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond `!in` (instruction as SwitchInst).branches.keys.map { value(it) }
                    })
                    if (paths.any { mutated in it }) mutated else null
                }
            }
            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }
        is TableSwitchInst -> when (predicate) {
            is DefaultSwitchPredicate -> {
                val cond = predicate.cond
                val candidates = instruction.range.toSet()
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result
            }
            is EqualityPredicate -> {
                val (cond, value) = predicate.lhv to (predicate.rhv as ConstIntTerm).value
                val candidates = instruction.range.toSet() - value
                var result: Clause? = null
                for (candidate in candidates) {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond equality candidate
                    })
                    result = if (paths.any { mutated in it }) null else mutated
                }
                result ?: run {
                    val mutated = Clause(instruction, path(instruction.location) {
                        cond `!in` (instruction as SwitchInst).branches.keys.map { value(it) }
                    })
                    if (paths.any { mutated in it }) null else mutated
                }
            }
            else -> unreachable { log.error("Unexpected predicate in switch clause: $predicate") }
        }
        else -> null
    }

    private fun mutateState(state: SymbolicState): SymbolicState? {
        val predicateState = state.state as BasicState
        val mutatedPathCondition = state.path.toMutableList()
        var dropping = false
        var clause: Clause? = null
        val newState = predicateState.dropLastWhile {
            if (it.type is PredicateType.Path) {
                val instruction = state[it]
                val reversed = Clause(instruction, it).reversed()
                if (reversed != null) {
                    clause = reversed
                    mutatedPathCondition.removeLast()
                    mutatedPathCondition += clause!!
                    if (PathConditionImpl(mutatedPathCondition) !in candidates)
                        dropping = true
                    else {
                        clause = null
                        mutatedPathCondition.removeLast()
                    }
                } else {
                    mutatedPathCondition.removeLast()
                }
            }
            dropping
        }
        if (clause == null) return null

        val mutatedState = newState.dropLast(1) + clause!!.predicate
        val mutatedValueMap = state.concreteValueMap.toMutableMap()
        candidates += PathConditionImpl(mutatedPathCondition)
        clause!!.predicate.operands.forEach {
            mutatedValueMap.remove(it)
        }

        return SymbolicStateImpl(
            mutatedState,
            PathConditionImpl(mutatedPathCondition),
            mutatedValueMap,
            state.termMap,
            state.predicateMap,
            InstructionTrace()
        )
    }

    private fun prepareState(method: Method, state: PredicateState): PredicateState = transform(state) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ConstStringAdapter()
        +FieldNormalizer(method.cm)
    }

    private fun check(method: Method, state: SymbolicState): SymbolicState? {
        val checker = Checker(method, ctx, PredicateStateAnalysis(cm))
        val preparedState = prepareState(method, state.state)
        val result = checker.check(preparedState, state.state.path)
        if (result !is Result.SatResult) return null

        val parameters = tryOrNull {
            generator.generate("test", method, checker.state, result.model)
        } ?: return null

        return collectTrace(method, parameters) ?: return null
    }

    private fun processMethod(method: Method) {
        val stateDeque = dequeOf<SymbolicState>()
        getRandomTrace(method)?.let {
            stateDeque += it
            paths += it.path
            traceManager.addTrace(method, it.trace)
        }
        while (stateDeque.isNotEmpty() && !traceManager.isFullCovered(method)) {
            val state = stateDeque.pollFirst()
            log.debug("Processing state: $state")

            val mutatedState = mutateState(state) ?: continue
            log.debug("Mutated state: $mutatedState")

            val newState = check(method, mutatedState) ?: continue
            if (newState.path !in paths) {
                log.debug("New state: $newState")

                stateDeque += newState
                paths += newState.path
            }
            traceManager.addTrace(method, newState.trace)
        }
    }

}