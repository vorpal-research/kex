package org.jetbrains.research.kex.asm.analysis

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.originalBlock
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.ReanimatedModel
import org.jetbrains.research.kex.smt.model.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.trace.runner.ObjectTracingRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.FieldLoadInst
import org.jetbrains.research.kfg.ir.value.instruction.FieldStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.UnreachableInst
import org.jetbrains.research.kfg.util.DominatorTreeBuilder
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.nio.file.Files
import java.nio.file.Paths

private val failDir by lazy { kexConfig.getStringValue("debug", "dump-directory", "./fail") }

class KexCheckerException(val inner: Exception, val reason: PredicateState) : Exception()
class KexRunnerException(val inner: Exception, val model: ReanimatedModel) : Exception()

@Serializable
data class Failure(
        @ContextualSerialization val `class`: Class,
        @ContextualSerialization val method: Method,
        val message: String,
        val state: PredicateState
)

val Method.isImpactable: Boolean
    get() {
        when {
            this.isAbstract -> return false
            this.isStatic && this.argTypes.isEmpty() -> return false
            this.argTypes.isEmpty() -> {
                val thisVal = this.cm.value.getThis(this.`class`)
                for (inst in this.flatten()) {
                    when (inst) {
                        is FieldLoadInst -> if (inst.hasOwner && inst.owner == thisVal) return true
                        is FieldStoreInst -> if (inst.hasOwner && inst.owner == thisVal) return true
                        is CallInst -> if (!inst.isStatic && inst.callee == thisVal) return true
                    }
                }
                return false
            }
            else -> return true
        }
    }

class MethodChecker(
        override val cm: ClassManager,
        private val loader: ClassLoader,
        private val tm: TraceManager<Trace>,
        private val psa: PredicateStateAnalysis) : MethodVisitor {
    val random: Randomizer = defaultRandomizer

    @ImplicitReflectionSerializer
    private fun dumpPS(method: Method, message: String, state: PredicateState) {
        val failDirPath = Paths.get(failDir)
        if (!Files.exists(failDirPath)) {
            Files.createDirectory(failDirPath)
        }
        val errorDump = Files.createTempFile(failDirPath, "ps-", ".json").toFile()
        log.error("Failing saved to file ${errorDump.path}")
        errorDump.writeText(KexSerializer(cm).toJson(Failure(method.`class`, method, message, state)))
    }

    override fun cleanup() {}

    @ImplicitReflectionSerializer
    override fun visit(method: Method) {
        super.visit(method)

        if (method.`class`.isSynthetic) return
        if (method.isAbstract || method.isConstructor || method.isStaticInitializer) return
        if (!method.isImpactable) return

        log.debug("Checking method $method")
        log.debug(method.print())
        log.debug()

        val unreachableBlocks = mutableSetOf<BasicBlock>()
        val domTree = DominatorTreeBuilder(method).build()
        val order: SearchStrategy = DfsStrategy(method)

        for (block in order) {
            if (block.terminator is UnreachableInst) {
                unreachableBlocks += block
                continue
            }

            val originalBlock = block.originalBlock
            if (tm.isCovered(method, originalBlock)) continue

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

            log.debug("Block ${block.name} is covered = ${tm.isCovered(method, originalBlock)}")
            log.debug()

            if (coverageResult is Result.UnsatResult) unreachableBlocks += block
        }
        cleanup()
    }

    @ImplicitReflectionSerializer
    private fun coverBlock(method: Method, block: BasicBlock): Result {
        val checker = Checker(method, loader, psa)
        val ps = checker.createState(block.terminator)
                ?: return Result.UnknownResult("Could not create a predicate state for instruction")

        val result = try {
            checker.check(ps)
        } catch (e: Exception) {
            throw KexCheckerException(e, ps)
        }
        when (result) {
            is Result.SatResult -> {
                val (instance, args) = try {
                    generateByModel(method, checker.state, result.model)
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

    private fun generateByModel(method: Method, ps: PredicateState, model: SMTModel): Pair<Any?, Array<Any?>> {
        val reanimated = executeModel(ps, cm.type, method, model, loader, random)
        log.debug("Reanimated: ${tryOrNull { model.toString() }}")

        val instance = reanimated.instance ?: when {
            method.isStatic -> null
            else -> tryOrNull {
                val klass = loader.loadClass(types.getRefType(method.`class`))
                random.next(klass)
            }
        }

        if (instance == null && !method.isStatic) {
            throw GenerationException("Unable to create or generate instance of class ${method.`class`}")
        }
        return instance to reanimated.arguments.toTypedArray()
    }

    private fun collectTrace(method: Method, instance: Any?, args: Array<Any?>) {
        val runner = ObjectTracingRunner(method, loader)
        val trace = runner.collectTrace(instance, args)
        tm[method] = trace
    }
}
