package org.jetbrains.research.kex.asm.analysis.testgen

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.Reanimator
import org.jetbrains.research.kex.reanimator.codegen.validName
import org.jetbrains.research.kex.reanimator.descriptor.concretize
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull

class DescriptorChecker(
        ctx: ExecutionContext,
        tm: TraceManager<Trace>,
        psa: PredicateStateAnalysis) : MethodChecker(ctx, tm, psa) {

    override fun initializeGenerator(method: Method) {
        generator = Reanimator(ctx, psa, method)
    }

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
                    generator.generate("test_${block.validName}", method, checker.state, result.model)
                } catch (e: GenerationException) {
                    log.warn(e.message)
                    return result
                }

                try {
                    collectTrace(method, instance, args)
                } catch (e: TimeoutException) {
                    throw e
                } catch (e: Exception) {
                    throw KexRunnerException(e, Parameters(instance, args, setOf()))
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
        val staticTypeInfoMap = collectStaticTypeInfo(types, state, typeInfoMap)
        return prepareState(method, state, staticTypeInfoMap)
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


    fun Set<TypeInfo>.concretize(): Set<TypeInfo> = this.map { tryOrNull { it.concretize() } ?: it }.toSet()
    fun TypeInfo.concretize(): TypeInfo = when (this) {
        is CastTypeInfo -> CastTypeInfo(this.type.concretize(cm))
        else -> this
    }
}