package org.jetbrains.research.kex.asm.analysis

import kotlinx.serialization.ContextualSerialization
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.asm.transform.originalBlock
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.defaultRandomizer
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.SimpleRunner
import org.jetbrains.research.kex.trace.runner.TimeoutException
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.getClass
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.util.writeClass
import org.jetbrains.research.kfg.visitor.MethodVisitor
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

private val failDir by lazy { kexConfig.getStringValue("debug", "dump-directory", "./fail") }

class KexCheckerException(val inner: Exception, val reason: PredicateState) : Exception()

@Serializable
data class Failure(
        @ContextualSerialization val `class`: Class,
        @ContextualSerialization val method: Method,
        val message: String,
        val state: PredicateState
)

class MethodChecker(
        override val cm: ClassManager,
        private val loader: ClassLoader,
        private val originalCM: ClassManager,
        private val target: File,
        private val psa: PredicateStateAnalysis) : MethodVisitor {
    private val tm = TraceManager
    private val random = defaultRandomizer
    private var state: State? = null

    private data class State(
            val `class`: Class,
            val method: Method,
            val loader: ClassLoader,
            val traces: List<Instruction>
    )

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

    override fun cleanup() {
        val `class` = state?.`class` ?: return
        val classFileName = "${target.canonicalPath}/${`class`.fullname}.class"

        state?.traces?.forEach { it.parent?.remove(it) }

        writeClass(cm, loader, `class`, classFileName)
        state = null
    }

    @ImplicitReflectionSerializer
    override fun visit(method: Method) {
        super.visit(method)
        prepareMethodInfo(method)

        if (method.`class`.isSynthetic) return
        if (method.isAbstract || method.isConstructor) return
        // don't consider static parameters
        if (method.isStatic && method.argTypes.isEmpty()) return

        log.debug("Checking method $method")
        log.debug(method.print())
        log.debug()

        for (block in method.bodyBlocks) {
            val originalBlock = block.originalBlock
            if (tm.isCovered(method, originalBlock)) continue

            try {
                log.debug("Checking reachability of ${block.terminator.print()}")
                coverBlock(method, block)
            } catch (e: TimeoutException) {
                log.warn("Timeout exception when running method $method, skipping it")
                break
            } catch (e: KexCheckerException) {
                log.error("Fail when covering block ${block.name} of $method")
                log.error("Error: ${e.inner}")

                val failDirPath = Paths.get(failDir)
                if (!Files.exists(failDirPath)) {
                    Files.createDirectory(failDirPath)
                }
                val errorDump = Files.createTempFile(failDirPath, "ps-", ".json").toFile()
                log.error("Failing saved to file ${errorDump.path}")
                errorDump.writeText(KexSerializer(cm).toJson(Failure(method.`class`, method, e.inner.toString(), e.reason)))
                break
            }

            log.debug("Block ${block.name} is covered = ${tm.isCovered(method, originalBlock)}")
            log.debug()
        }
        cleanup()
    }

    @ImplicitReflectionSerializer
    private fun coverBlock(method: Method, block: BasicBlock) {
        val checker = Checker(method, state!!.loader, psa)
        val ps = checker.createState(block.terminator) ?: return

        try {
            val result = checker.check(ps)
            log.debug(result)

            when (result) {
                is Result.SatResult -> {
                    log.debug(result.model)
                    val model = executeModel(checker.state, method, result.model, state!!.loader)
                    log.debug("Recovered: ${tryOrNull { model.toString() }}")

                    val instance = model.instance ?: when {
                        method.isStatic -> null
                        else -> tryOrNull {
                            val klass = getClass(types.getRefType(method.`class`), state!!.loader)
                            random.next(klass)
                        }
                    }

                    if (instance == null && !method.isStatic) {
                        log.warn("Unable to create or generate instance of class ${method.`class`}")
                        return
                    }

                    val trace = SimpleRunner(method, state!!.loader).invoke(instance, model.arguments.toTypedArray())
                    tm.addTrace(method, trace)
                }
                is Result.UnsatResult -> log.debug("Instruction ${block.terminator.print()} is unreachable")
                is Result.UnknownResult -> log.debug("Can't decide on reachability of " +
                        "instruction ${block.terminator.print()}, reason: ${result.reason}")
            }
        } catch (e: TimeoutException) {
            throw e
        } catch(e: Exception) {
            throw KexCheckerException(e, ps)
        }
    }
}
