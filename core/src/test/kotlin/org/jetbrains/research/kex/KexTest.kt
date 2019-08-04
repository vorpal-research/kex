package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.term.ConstBoolTerm
import org.jetbrains.research.kex.state.term.ConstIntTerm
import org.jetbrains.research.kex.state.term.isConst
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.test.Intrinsics
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
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
    val cm: ClassManager
    private val loader: ClassLoader

    init {
        val rootDir = System.getProperty("root.dir")
        val version = System.getProperty("project.version")
        kexConfig.initialize(RuntimeConfig, FileConfig("$rootDir/kex-test.ini"))
        kexConfig.initLog("$rootDir/kex-test.log")
        //        RuntimeConfig.setValue("z3", "tacticsFile", "$rootDir/z3.tactics")
        RuntimeConfig.setValue("boolector", "", "")
        val jarPath = "$rootDir/kex-test/target/kex-test-$version-jar-with-dependencies.jar"
        val jarFile = JarFile(jarPath)
        loader = jarFile.classLoader
        val `package` = Package("$packageName/*")
        cm = ClassManager(jarFile, `package`, Flags.readAll)
    }

    protected fun getPSA(method: Method): PredicateStateAnalysis {
        val loops = LoopAnalysis(cm).invoke(method)
        if (loops.isNotEmpty()) {
            LoopSimplifier(cm).visit(method)
            LoopDeroller(cm).visit(method)
        }

        val psa = PredicateStateAnalysis(cm)
        psa.visit(method)
        return psa
    }

    protected fun getReachables(method: Method): List<Instruction> {
        val `class` = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = cm.getByName(`class`)

        val types = cm.type
        val methodName = Intrinsics::assertReachable.name
        val desc = MethodDesc(arrayOf(types.getArrayType(types.boolType)), types.voidType)
        val assertReachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertReachable && it.`class` == intrinsics }
                .toList()
    }

    protected fun getUnreachables(method: Method): List<Instruction> {
        val `class` = Intrinsics::class.qualifiedName!!.replace(".", "/")
        val intrinsics = cm.getByName(`class`)

        val methodName = Intrinsics::assertUnreachable.name
        val desc = MethodDesc(arrayOf(), cm.type.voidType)
        val assertUnreachable = intrinsics.getMethod(methodName, desc)
        return method.flatten().asSequence()
                .mapNotNull { it as? CallInst }
                .filter { it.method == assertUnreachable && it.`class` == intrinsics }
                .toList()
    }

    fun testClassReachability(`class`: Class) {
        `class`.methods.forEach { (_, method) ->
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
                val result = checker.checkReachable(inst)
                assertTrue(result is Result.UnsatResult, "Class $`class`; method $method; ${inst.print()} should be unreachable")
            }
        }
    }
}