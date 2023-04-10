package org.vorpal.research.kex.asm.analysis.bmc

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.analysis.DfsStrategy
import org.vorpal.research.kex.asm.analysis.SearchStrategy
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.compile.CompilerHelper
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.random.GenerationException
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kex.reanimator.ParameterGenerator
import org.vorpal.research.kex.reanimator.UnsafeGenerator
import org.vorpal.research.kex.reanimator.codegen.klassName
import org.vorpal.research.kex.reanimator.codegen.validName
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.trace.TraceManager
import org.vorpal.research.kex.trace.`object`.ActionTrace
import org.vorpal.research.kex.trace.runner.ObjectTracingRunner
import org.vorpal.research.kex.util.TimeoutException
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.ir.value.instruction.UnreachableInst
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.graph.DominatorTreeBuilder
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Files

private val failDir by lazy { kexConfig.getPathValue("debug", "dumpDirectory", "fail") }

class KexCheckerException(val inner: Exception, val reason: PredicateState) : Exception()
class KexRunnerException(val inner: Exception, val model: Parameters<Any?>) : Exception()

@Serializable
data class Failure(
        @Contextual val `class`: Class,
        @Contextual val method: Method,
        val message: String,
        val state: PredicateState
)

@Suppress("MemberVisibilityCanBePrivate")
@ExperimentalSerializationApi
@InternalSerializationApi
open class MethodChecker(
    val ctx: ExecutionContext,
    protected val tm: TraceManager<ActionTrace>,
    protected val psa: PredicateStateAnalysis) : MethodVisitor {
    protected val nameContext = NameMapperContext()
    override val cm: ClassManager get() = ctx.cm
    val random: Randomizer get() = ctx.random
    val loader: ClassLoader get() = ctx.loader
    private val compilerHelper = CompilerHelper(ctx)
    lateinit var generator: ParameterGenerator
        protected set

    private fun dumpPS(method: Method, message: String, state: PredicateState) = `try` {
        val failDirPath = kexConfig.outputDirectory.resolve(failDir)
        if (!Files.exists(failDirPath)) {
            Files.createDirectory(failDirPath)
        }
        val errorDump = Files.createTempFile(failDirPath, "ps-", ".json").toFile()
        log.error("Failing saved to file ${errorDump.path}")
        errorDump.writeText(KexSerializer(cm).toJson(Failure(method.klass, method, message, state)))
    }.getOrNull()

    override fun cleanup() {
        nameContext.clear()
    }

    protected open fun initializeGenerator(method: Method) {
        generator = UnsafeGenerator(ctx, method, method.klassName)
    }

    protected open fun getSearchStrategy(method: Method): SearchStrategy = DfsStrategy(method)

    override fun visit(method: Method) {
        super.visit(method)

        if (!MethodManager.canBeImpacted(method, ctx.accessLevel) || !method.hasBody) return

        log.debug("Checking method $method")
        log.debug(method.print())
        log.debug()

        val unreachableBlocks = mutableSetOf<BasicBlock>()
        val domTree = DominatorTreeBuilder(method.body).build()
        val order: SearchStrategy = getSearchStrategy(method)

        initializeGenerator(method)

        for (block in order) {
            if (block.terminator is UnreachableInst) {
                unreachableBlocks += block
                continue
            }

            if (tm.isCovered(block)) continue

            if (block in unreachableBlocks) continue
            if (domTree[block]?.idom?.value in unreachableBlocks) {
                unreachableBlocks += block
                continue
            }

            val coverageResult = try {
                log.debug("Checking reachability of ${block.name}")
                coverBlock(method, block)
            } catch (e: TimeoutException) {
                log.warn("Timeout exception when running method $method, skipping it")
                break
            } catch (e: KexCheckerException) {
                log.error("Fail when covering block ${block.name} of $method")
                log.error("Error: ${e.inner}")
                dumpPS(method, e.inner.toString(), e.reason)
                break
            } catch (e: KexRunnerException) {
                log.error("Fail when running method $method with model ${e.model}")
                log.error("Error: ${e.inner}")
                break
            }

            log.debug("Block ${block.name} is covered = ${tm.isCovered(block)}")
            log.debug()

            if (coverageResult is Result.UnsatResult) unreachableBlocks += block
        }

        val testFile = generator.emit()
        tryOrNull { compilerHelper.compileFile(testFile) }
    }

    protected open fun coverBlock(method: Method, block: BasicBlock): Result {
        val checker = Checker(method, ctx, psa)
        val ps = checker.createState(block.terminator)
                ?: return Result.UnknownResult("Could not create a predicate state for instruction")

        val result = try {
            checker.prepareAndCheck(ps)
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

    protected fun collectTrace(method: Method, instance: Any?, args: List<Any?>) = tryOrNull {
        val params = Parameters(instance, args)
        val runner = ObjectTracingRunner(nameContext, method, loader, params)
        val trace = runner.run() ?: return null
        tm[method] = trace
    }
}
