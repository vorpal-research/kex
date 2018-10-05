package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.state.PredicateStateBuilder
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kex.state.term.isConst
import org.jetbrains.research.kex.test.Intrinsics
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.classLoader
import java.util.jar.JarFile
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class KexTest {
    val packageName = "org/jetbrains/research/kex/test"
    private val loader: ClassLoader

    init {
        val rootDir = System.getProperty("root.dir")
        GlobalConfig.initialize(RuntimeConfig, FileConfig("$rootDir/kex-test.ini"))
        RuntimeConfig.setValue("z3", "tacticsFile", "$rootDir/z3.tactics")

        val jarPath = "$rootDir/kex-test/target/kex-test-0.1-jar-with-dependencies.jar"
        val jarFile = JarFile(jarPath)
        loader = jarFile.classLoader
        val `package` = Package("$packageName/*")
        CM.parseJar(jarFile, `package`, Flags.readAll)
    }

    private fun getPSA(method: Method): PredicateStateBuilder {
        val loops = LoopAnalysis.invoke(method)
        if (loops.isNotEmpty()) {
            LoopSimplifier.visit(method)
            LoopDeroller.visit(method)
        }

        val psa = PredicateStateAnalysis
        psa.visit(method)
        return psa.builder(method)
    }

    private fun getReachables(method: Method): List<Instruction> {
        val `class` = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = CM.getByName(`class`)

        val methodName = Intrinsics::assertReachable.name
        val desc = MethodDesc(arrayOf(TF.getArrayType(TF.boolType)), TF.voidType)
        val assertReachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertReachable && it.`class` == intrinsics }
                .toList()
    }

    private fun getUnreachables(method: Method): List<Instruction> {
        val `class` = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = CM.getByName(`class`)

        val methodName = Intrinsics::assertUnreachable.name
        val desc = MethodDesc(arrayOf(), TF.voidType)
        val assertUnreachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertUnreachable && it.`class` == intrinsics }
                .toList()
    }

    fun testClassReachability(`class`: Class) {
        `class`.methods.forEach { _, method ->
            log.debug("Checking method $method")
            log.debug(method.print())

            val psa = getPSA(method)
            val checker = Checker(method, loader, psa)

            getReachables(method).forEach { inst ->
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

                val model = (result as Result.SatResult).model
                log.debug("Acquired model: $model")
                log.debug("Checked assertions: $assertions")
                for (it in assertions) {
                    val argTerm = TermFactory.getValue(it)

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
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.UnsatResult, "Class $`class`; method $method; ${inst.print()} should be unreachable")
            }
        }
    }
}