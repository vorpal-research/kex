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
import org.jetbrains.research.kex.util.getIntrinsics
import org.jetbrains.research.kex.util.getRuntime
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

@OptIn(ExperimentalStdlibApi::class)
abstract class KexRunnerTest(
    private val initTR: Boolean = false,
    private val initIntrinsics: Boolean = false,
    additionJarName: String? = null
) : KexTest(initTR, initIntrinsics, additionJarName) {
    val targetDir = Files.createTempDirectory("kex-test")

    val analysisContext: ExecutionContext
    val originalContext: ExecutionContext

    init {
        val jar = Paths.get(jarPath).asContainer(`package`)!!
        val origManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))

        jar.unpack(cm, targetDir, true)
        additionContainers.forEach { it.unpack(cm, targetDir, true) }

        val containersToInit = buildList {
            add(jar)
            if (initTR) add(getRuntime()!!)
            if (initIntrinsics) add(getIntrinsics()!!)
            if (additionContainers.isNotEmpty()) addAll(additionContainers)
        }.toTypedArray()
        origManager.initialize(*containersToInit)
        val classLoader = URLClassLoader(arrayOf(targetDir.toUri().toURL()))
        originalContext = ExecutionContext(origManager, `package`, jar.classLoader, EasyRandomDriver(), listOf())

        executePipeline(originalContext.cm, `package`) {
            +RuntimeTraceCollector(originalContext.cm)
            +ClassWriter(originalContext, targetDir)
        }

        analysisContext = ExecutionContext(cm, `package`, classLoader, EasyRandomDriver(), listOf())
    }

    protected fun getReachables(method: Method): List<Instruction> {
        val klass = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = cm[klass]

        val types = cm.type
        val methodName = "kexAssert"
        val desc = MethodDesc(arrayOf(types.getArrayType(types.boolType)), types.voidType)
        val assertReachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertReachable && it.klass == intrinsics }
                .toList()
    }

    protected fun getUnreachables(method: Method): List<Instruction> {
        val klass = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = cm[klass]

        val methodName = "kexUnreachable"
        val desc = MethodDesc(arrayOf(), cm.type.voidType)
        val assertUnreachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertUnreachable && it.klass == intrinsics }
                .toList()
    }

    fun testClassReachability(klass: Class) {
        klass.allMethods.forEach { method ->
            log.debug("Checking method $method")
            log.debug(method.print())

            val psa = getPSA(method)
            val ctx = ExecutionContext(cm, `package`, loader, EasyRandomDriver(), listOf())

            getReachables(method).forEach { inst ->
                val checker = Checker(method, ctx, psa)
                val state = checker.createState(inst) ?: return
                val result = checker.prepareAndCheck(state)
                assertTrue(result is Result.SatResult, "Class $klass; method $method; ${inst.print()} should be reachable")

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
                val checker = Checker(method, ctx, psa)
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.UnsatResult, "Class $klass; method $method; ${inst.print()} should be unreachable")
            }
        }
    }

    fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "${classPath.split(":").filter { "kex-test" !in it }.joinToString(":")}:$urlClassPath")
    }

    fun clearClassPath() {
        System.setProperty("java.class.path", classPath)
    }

    fun runPipelineOn(klass: Class) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)

        updateClassPath(analysisContext.loader as URLClassLoader)
        executePipeline(analysisContext.cm, klass) {
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodChecker(analysisContext, traceManager, psa)
            // todo: add check that generation is actually successful
        }
        clearClassPath()
    }
}