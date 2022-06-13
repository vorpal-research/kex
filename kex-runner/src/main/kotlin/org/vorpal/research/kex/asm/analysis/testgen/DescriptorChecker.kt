package org.vorpal.research.kex.asm.analysis.testgen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.reanimator.Reanimator
import org.vorpal.research.kex.reanimator.codegen.validName
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.transformer.*
import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kex.trace.`object`.ActionTrace
import org.vorpal.research.kex.util.TimeoutException
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull

@ExperimentalSerializationApi
@InternalSerializationApi
class DescriptorChecker(
    ctx: ExecutionContext,
    tm: TraceManager<ActionTrace>,
    psa: PredicateStateAnalysis
) : MethodChecker(ctx, tm, psa) {

    override fun initializeGenerator(method: Method) {
        generator = Reanimator(ctx, psa, method)
    }

    override fun coverBlock(method: Method, block: BasicBlock): Result {
        val checker = Checker(method, ctx, psa)
        val ps = checker.createState(method, block)
            ?: return Result.UnknownResult("Could not resolve types for ${block.name}")

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
            is Result.UnknownResult -> log.debug(
                "Can't decide on reachability of " +
                        "instruction ${block.terminator.print()}, reason: ${result.reason}"
            )
        }
        return result
    }

    private val TypeInfoMap.rtMapped
        get() = mapValues {
            it.value.map { ti ->
                when (ti) {
                    is NullabilityInfo -> ti
                    is CastTypeInfo -> CastTypeInfo(ti.type.rtMapped)
                }
            }.toSet()
        }

    private fun Checker.createState(method: Method, block: BasicBlock): PredicateState? {
        val typeInfoMap = resolveTypes(method, block) ?: return null
        val state = this.createState(block.terminator)!!
        val staticTypeInfoMap = collectStaticTypeInfo(types, state, typeInfoMap)
        return prepareState(method, state, staticTypeInfoMap.rtMapped)
    }

    private fun resolveTypes(method: Method, block: BasicBlock): TypeInfoMap? {
        val checker = Checker(method, ctx, psa)
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


    private fun Set<TypeInfo>.concretize(): Set<TypeInfo> = this.map { tryOrNull { it.concretize() } ?: it }.toSet()
    private fun TypeInfo.concretize(): TypeInfo = when (this) {
        is CastTypeInfo -> CastTypeInfo(instantiationManager.getConcreteType(this.type, cm, ctx.random))
        else -> this
    }
}