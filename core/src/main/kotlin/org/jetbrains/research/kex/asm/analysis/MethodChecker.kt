package org.jetbrains.research.kex.asm.analysis

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.asm.transform.originalBlock
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.ReanimatedModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.SimpleRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kex.util.*
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.util.DominatorTreeBuilder
import org.jetbrains.research.kfg.util.writeClass
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.io.File
import java.net.URLClassLoader
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
        private val originalCM: ClassManager,
        private val target: File,
        private val psa: PredicateStateAnalysis) : MethodVisitor {
    private val tm = TraceManager
    private var state: State? = null

    private data class State(
            val `class`: Class,
            val method: Method,
            val loader: ClassLoader,
            val traces: List<Instruction>
    ) {
        private val oldClassPath = System.getProperty("java.class.path")
        val random: Randomizer get() = defaultRandomizer

        init {
            /**
             * This is fucked up, but it is needed so that randomizer can scan all the classes from
             * @loader to be able to generate random instances of target classes
             */
            updateClassPath()
        }

        private fun updateClassPath() {
            val urlLoader = loader as? URLClassLoader ?: unreachable { log.error("Unknown ClassLoader type in State") }
            val urlClassPath = urlLoader.urLs.joinToString(separator = ":") { "${it.path}." }
            System.setProperty("java.class.path", "$oldClassPath:$urlClassPath")
        }

        fun clearClassPath() {
            System.setProperty("java.class.path", oldClassPath)
        }
    }

    private fun prepareMethodInfo(method: Method) {
        val originalClass = originalCM.getByName(method.`class`.fullname)
        val originalMethod = originalClass.getMethod(method.name, method.desc)
        val classFileName = "${target.canonicalPath}/${originalClass.fullname}.class"

        if (!method.isAbstract && !method.isConstructor) {
            val traceInstructions = TraceInstrumenter(originalCM).invoke(originalMethod)
            writeClass(cm, loader, originalClass, classFileName)
            val directory = URLClassLoader(arrayOf(target.toURI().toURL()))

            state = State(originalClass, originalMethod, directory, traceInstructions)
        }
    }

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

    override fun cleanup() {
        val `class` = state?.`class` ?: return
        val classFileName = "${target.canonicalPath}/${`class`.fullname}.class"

        state?.traces?.forEach { it.parent?.remove(it) }

        writeClass(cm, loader, `class`, classFileName)
        state?.clearClassPath()
        state = null
    }

    @ImplicitReflectionSerializer
    override fun visit(method: Method) {
        super.visit(method)

        if (method.`class`.isSynthetic) return
        if (method.isAbstract || method.isConstructor) return
        if (!method.isImpactable) return

        log.debug("Checking method $method")
        log.debug(method.print())
        log.debug()

        prepareMethodInfo(method)

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
        val loader = state!!.loader
        val random = state!!.random
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
                val model = executeModel(checker.state, cm.type, method, result.model, loader, random)
                log.debug("Reanimated: ${tryOrNull { model.toString() }}")

                val instance = model.instance ?: when {
                    method.isStatic -> null
                    else -> tryOrNull {
                        val klass = loader.loadClass(types.getRefType(method.`class`))
                        random.next(klass)
                    }
                }

                if (instance == null && !method.isStatic) {
                    log.warn("Unable to create or generate instance of class ${method.`class`}")
                    return result
                }

                try {
                    val trace = SimpleRunner(method, loader).invoke(instance, model.arguments.toTypedArray())
                    tm.addTrace(method, trace)
                } catch (e: TimeoutException) {
                    throw e
                } catch (e: Exception) {
                    throw KexRunnerException(e, ReanimatedModel(method, instance, model.arguments))
                }
            }
            is Result.UnsatResult -> log.debug("Instruction ${block.terminator.print()} is unreachable")
            is Result.UnknownResult -> log.debug("Can't decide on reachability of " +
                    "instruction ${block.terminator.print()}, reason: ${result.reason}")
        }
        return result
    }
}
