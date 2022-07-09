package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.asm.analysis.testgen.MethodChecker
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asm.transform.LoopDeroller
import org.vorpal.research.kex.asm.transform.RuntimeTraceCollector
import org.vorpal.research.kex.asm.transform.SystemExitTransformer
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.ClassWriter
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.intrinsics.AssertIntrinsics
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.isConst
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.trace.`object`.ObjectTraceManager
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.analysis.LoopSimplifier
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalSerializationApi
@InternalSerializationApi
abstract class KexRunnerTest : KexTest() {
    val classPath = System.getProperty("java.class.path")
    val targetDir = Files.createTempDirectory("kex-test")
    val analysisContext: ExecutionContext

    init {
        val jar = Paths.get(jarPath).asContainer(`package`)!!

        jar.unpack(cm, targetDir, true)
        val classLoader = URLClassLoader(arrayOf(targetDir.toUri().toURL()))

        val instrumentedDirName = kexConfig.getStringValue("output", "instrumentedDir", "instrumented")
        val instrumentedCodeDir = kexConfig.getPathValue("kex", "outputDir")!!.resolve(instrumentedDirName)
        prepareInstrumentedClasspath(jar, jar.classLoader, Package.defaultPackage, instrumentedCodeDir)

        analysisContext = ExecutionContext(cm, `package`, classLoader, EasyRandomDriver(), listOf(jar.path), AccessModifier.Private)
    }

    private fun prepareInstrumentedClasspath(container: Container, containerClassLoader: ClassLoader, target: Package, path: Path) {
        log.info("Preparing ${container.path}")
        val cm = ClassManager(
            KfgConfig(
                flags = Flags.readAll,
                useCachingLoopManager = false,
                failOnError = false,
                verifyIR = false,
                checkClasses = false
            )
        )
        cm.initialize(container)
        val context = ExecutionContext(
            cm,
            target,
            containerClassLoader,
            EasyRandomDriver(),
            listOf(container.path)
        )

        container.unpack(cm, path, true)

        executePipeline(cm, target) {
            +SystemExitTransformer(cm)
            +createTraceCollector(context)
            +ClassWriter(context, path)
        }
    }

    protected open fun createTraceCollector(context: ExecutionContext): MethodVisitor = RuntimeTraceCollector(context.cm)

    protected fun getReachables(method: Method): List<Instruction> {
        val klass = AssertIntrinsics::class.qualifiedName!!.replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
        val intrinsics = cm[klass]

        val types = cm.type
        val methodName = "kexAssert"
        val assertReachable = intrinsics.getMethod(methodName, types.voidType, types.getArrayType(types.boolType))
        return method.body.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertReachable && it.klass == intrinsics }
                .toList()
    }

    protected fun getUnreachables(method: Method): List<Instruction> {
        val klass = AssertIntrinsics::class.qualifiedName!!.replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
        val intrinsics = cm[klass]

        val methodName = "kexUnreachable"
        val assertUnreachable = intrinsics.getMethod(methodName, cm.type.voidType)
        return method.body.flatten().asSequence()
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
                val assertions = method.body.flatten()
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

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        System.setProperty("java.class.path", "${classPath.split(":").filter { "kex-test" !in it }.joinToString(":")}:$urlClassPath")
    }

    private fun clearClassPath() {
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
