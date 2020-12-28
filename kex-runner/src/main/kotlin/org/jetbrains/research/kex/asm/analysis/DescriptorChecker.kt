package org.jetbrains.research.kex.asm.analysis

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.descriptor.concretize
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.ReanimatedModel
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.*
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
                    generator.generateAPI(block, checker.state, result.model)
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
        val typeInfoMap = resolveTypes(method, block) ?: return null
        val state = this.createState(block.terminator)!!
        return prepareState(method, state, typeInfoMap)
    }

    protected fun resolveTypes(method: Method, block: BasicBlock): TypeInfoMap? {
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
                    val typeInfoMap = generateFinalTypeInfoMap(method, ctx, result.model, checker.state)
                    typeInfoMap.mapKeys { it.key.dropMemspace() }.mapValues { it.value.concretize() }
                } catch (e: Exception) {
                    log.warn("$e")
                    null
                }
            }
            is Result.UnsatResult -> null
            is Result.UnknownResult -> null
        }
    }


    fun Set<TypeInfo>.concretize(): Set<TypeInfo> = this.map { it.concretize() }.toSet()
    fun TypeInfo.concretize(): TypeInfo = when (this) {
        is CastTypeInfo -> CastTypeInfo(this.type.concretize(cm))
        else -> this
    }
}