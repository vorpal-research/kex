package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.analysis.testgen.MethodChecker
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.RuntimeTraceCollector
import org.jetbrains.research.kex.asm.util.ClassWriter
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.isConst
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.trace.`object`.ObjectTraceManager
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.logging.log
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class KexRunnerTest : KexTest() {
    val classPath = System.getProperty("java.class.path")
    val targetDir = Files.createTempDirectory("kex-test")

    val analysisContext: ExecutionContext
    val originalContext: ExecutionContext

    init {
        val jar = Paths.get(jarPath).asContainer(`package`)!!
        val origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))

        jar.unpack(cm, targetDir, true)
        val classLoader = URLClassLoader(arrayOf(targetDir.toUri().toURL()))
        originalContext = ExecutionContext(origManager, jar.classLoader, EasyRandomDriver())

        executePipeline(originalContext.cm, `package`) {
            +RuntimeTraceCollector(originalContext.cm)
            +ClassWriter(originalContext, targetDir)
        }

        analysisContext = ExecutionContext(cm, classLoader, EasyRandomDriver())
    }

    protected fun getReachables(method: Method): List<Instruction> {
        val `class` = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = cm[`class`]

        val types = cm.type
        val methodName = "kexAssert"
        val desc = MethodDesc(arrayOf(types.getArrayType(types.boolType)), types.voidType)
        val assertReachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertReachable && it.`class` == intrinsics }
                .toList()
    }

    protected fun getUnreachables(method: Method): List<Instruction> {
        val `class` = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = cm[`class`]

        val methodName = "kexUnreachable"
        val desc = MethodDesc(arrayOf(), cm.type.voidType)
        val assertUnreachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertUnreachable && it.`class` == intrinsics }
                .toList()
    }

    fun testClassReachability(`class`: Class) {
        `class`.allMethods.forEach { method ->
            log.debug("Checking method $method")
            log.debug(method.print())

            val psa = getPSA(method)

            getReachables(method).forEach { inst ->
                val checker = Checker(method, loader, psa)
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.SatResult, "Class $`class`; method $method; ${inst.print()} should be reachable")

                inst as CallInst
                val assertionsArray = inst.args.first()
                val assertions = method.flatten()
                        .asSequence()
                        .mapNotNull { it as? ArrayStoreInst }
                        .filter { it.arrayRef == assertionsArray }
                        .map { it.value }
                        .toList()

                val model = result.model
                log.debug("Acquired model: $model")
                log.debug("Checked assertions: $assertions")
                for (it in assertions) {
                    val argTerm = term { value(it) }

                    if (argTerm.isConst) continue

                    val modelValue = model.assignments[argTerm]
                    assertNotNull(modelValue)
                    assertTrue(
                            ((modelValue is ConstBoolTerm) && modelValue.value) ||
                                    (modelValue is ConstIntTerm) && modelValue.value > 0
                    )
                }
            }

            getUnreachables(method).forEach { inst ->
                val checker = Checker(method, loader, psa)
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.UnsatResult, "Class $`class`; method $method; ${inst.print()} should be unreachable")
            }
        }
    }

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "${classPath.split(":").filter { "kex-test" !in it }.joinToString(":")}:$urlClassPath")
    }

    private fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    fun runPipelineOn(`class`: Class) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)

        updateClassPath(analysisContext.loader as URLClassLoader)
        executePipeline(analysisContext.cm, `class`) {
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodChecker(analysisContext, traceManager, psa)
            // todo: add check that generation is actually successful
        }
        clearClassPath()
    }
}