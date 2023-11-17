package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.asm.analysis.bmc.MethodChecker
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asm.transform.LoopDeroller
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.intrinsics.AssertIntrinsics
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.term.ConstBoolTerm
import org.vorpal.research.kex.state.term.ConstIntTerm
import org.vorpal.research.kex.state.term.isConst
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.trace.`object`.ObjectTraceManager
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kfg.analysis.LoopSimplifier
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Paths
import kotlin.test.BeforeTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("HasPlatformType", "MemberVisibilityCanBePrivate")
@ExperimentalSerializationApi
@InternalSerializationApi
abstract class KexRunnerTest(
    testDirectoryName: String,
) : KexTest(testDirectoryName) {
    val classPath = System.getProperty("java.class.path")
    lateinit var analysisContext: ExecutionContext

    @BeforeTest
    fun init() {
        val jar = Paths.get(jarPath).asContainer(`package`)!!
        analysisContext = ExecutionContext(
            cm, jar.classLoader, EasyRandomDriver(), listOf(jar.path), AccessModifier.Private
        )
    }

    private fun getReachables(method: Method): List<Instruction> {
        val klass = AssertIntrinsics::class.qualifiedName!!.asmString
        val intrinsics = cm[klass]

        val types = cm.type
        val methodName = "kexAssert"
        val assertReachable = intrinsics.getMethod(methodName, types.voidType, types.boolType)
        return method.body.flatten().asSequence()
            .mapNotNull { it as? CallInst }
            .filter { it.method == assertReachable && it.klass == intrinsics }
            .toList()
    }

    private fun getUnreachables(method: Method): List<Instruction> {
        val klass = AssertIntrinsics::class.qualifiedName!!.asmString
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
            log.debug("Checking method {}", method)
            log.debug(method.print())

            val psa = getPSA(method)
            val ctx = ExecutionContext(cm, loader, EasyRandomDriver(), listOf())

            getReachables(method).forEach { inst ->
                val checker = Checker(method, ctx, psa)
                val state = checker.createState(inst) ?: return
                val result = checker.prepareAndCheck(state)
                assertTrue(
                    result is Result.SatResult,
                    "Class $klass; method $method; ${inst.print()} should be reachable"
                )

                inst as CallInst
                val assertionsArray = inst.args.first()
                val assertions = method.body.flatten()
                    .asSequence()
                    .mapNotNull { it as? ArrayStoreInst }
                    .filter { it.arrayRef == assertionsArray }
                    .map { it.value }

                val model = result.model
                log.debug("Acquired model: {}", model)
                log.debug("Checked assertions: {}", assertions)
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
                assertTrue(
                    result is Result.UnsatResult,
                    "Class $klass; method $method; ${inst.print()} should be unreachable"
                )
            }
        }
    }

    fun runPipelineOn(klass: Class) {
        val traceManager = ObjectTraceManager()
        val psa = PredicateStateAnalysis(analysisContext.cm)

        executePipeline(analysisContext.cm, klass) {
            +LoopSimplifier(analysisContext.cm)
            +LoopDeroller(analysisContext.cm)
            +psa
            +MethodChecker(analysisContext, traceManager, psa)
            // todo: add check that generation is actually successful
        }
    }
}
