package org.jetbrains.research.kex.asm.analysis

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.concreteParameters
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.ReanimatedModel
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.TermRemapper
import org.jetbrains.research.kex.state.transformer.collectArguments
import org.jetbrains.research.kex.state.transformer.collectPlainTypeInfos
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

class DescriptorChecker(
        ctx: ExecutionContext,
        tm: TraceManager<Trace>,
        psa: PredicateStateAnalysis) : MethodChecker(ctx, tm, psa) {

    override fun coverBlock(method: Method, block: BasicBlock): Result {
        val checker = Checker(method, ctx.loader, psa)
        val ps = checker.createState(method, block)
                ?: return Result.UnknownResult("Colud not resolve types for ${block.name}")

        val result = try {
            checker.check(ps, ps.path)
        } catch (e: Exception) {
            throw KexCheckerException(e, ps)
        }
        when (result) {
            is Result.SatResult -> {
                val (instance, args) = try {
                    generator.generateAPI(method, checker.state, result.model)
                } catch (e: GenerationException) {
                    log.warn(e.message)
                    return result
                }

                try {
                    collectTrace(method, instance, args)
                } catch (e: TimeoutException) {
                    throw e
                } catch (e: Exception) {
                    throw KexRunnerException(e, ReanimatedModel(method, instance, args.toList()))
                }
            }
            is Result.UnsatResult -> log.debug("Instruction ${block.terminator.print()} is unreachable")
            is Result.UnknownResult -> log.debug("Can't decide on reachability of " +
                    "instruction ${block.terminator.print()}, reason: ${result.reason}")
        }
        return result
    }

    private fun Checker.createState(method: Method, block: BasicBlock): PredicateState? {
        val typeInfoState = resolveTypes(method, block) ?: return null

        val ps = typeInfoState.let {
            val ps = this.createState(block.terminator)!!
            val (abstractThis, abstractArgs) = collectArguments(it)
            val (concreteThis, concreteArgs) = collectArguments(ps)
            val map = mutableMapOf<Term, Term>()
            concreteThis?.run { map[concreteThis] == abstractThis }
            concreteArgs.forEach { (key, value) -> abstractArgs[key]?.let { map[value] = it } }
            TermRemapper(map).apply(ps)
        }
        val typeInfoMap = collectPlainTypeInfos(ctx.types, typeInfoState)
        return prepareState(method, ps, typeInfoMap)
    }

    protected fun resolveTypes(method: Method, block: BasicBlock): PredicateState? {
        val checker = Checker(method, loader, psa)
        val ps = checker.createState(block.terminator) ?: return null

        val result = try {
            checker.prepareAndCheck(ps)
        } catch (e: Exception) {
            throw KexCheckerException(e, ps)
        }
        return when (result) {
            is Result.SatResult -> {
                try {
                    val parameters = generateFinalDescriptors(method, ctx, result.model, checker.state)
                    val concrete = parameters.concreteParameters(cm)
                    concrete.typeInfoState
                } catch (e: Exception) {
                    log.warn("$e")
                    null
                }
            }
            is Result.UnsatResult -> null
            is Result.UnknownResult -> null
        }
    }

    private val Parameters<Descriptor>.typeInfoState: PredicateState
        get() {
            val thisState = instance?.run {
                TermRemapper(mapOf(term to term { `this`(term.type) })).apply(typeInfo)
            }
            val argStates = arguments.mapIndexed { index, descriptor ->
                val typeInfo = descriptor.typeInfo
                TermRemapper(mapOf(descriptor.term to term { arg(descriptor.term.type, index) })).apply(typeInfo)
            }.toTypedArray()
            return listOfNotNull(thisState, *argStates).fold(emptyState()) { acc, predicateState -> acc + predicateState }
        }


}