package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.CallInst
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.util.Flags
import java.util.jar.JarFile

abstract class KexTest : Loggable {
    val packageName = "org/jetbrains/research/kex/test"

    init {
        val rootDir = System.getProperty("root.dir")
        GlobalConfig.initialize(RuntimeConfig, FileConfig("$rootDir/kex-test.ini"))

        val jarPath = "$rootDir/kex-test/target/kex-test-0.1-jar-with-dependencies.jar"
        val jarFile = JarFile(jarPath)
        val `package` = Package("$packageName/*")
        CM.parseJar(jarFile, `package`, Flags.getNoFrames())
    }

    fun getPSA(method: Method): PredicateStateAnalysis {
        val la = LoopAnalysis(method)
        la.visit()
        if (la.loops.isNotEmpty()) {
            val simplifier = LoopSimplifier(method)
            simplifier.visit()
            val deroller = LoopDeroller(method)
            deroller.visit()
        }

        val psa = PredicateStateAnalysis(method)
        psa.visit()
        return psa
    }

    fun getReachables(method: Method): List<Instruction> {
        val intrinsics = CM.getByName("$packageName/Intrinsics")
        val assertReachable = intrinsics.getMethod("assertReachable", "([Z)V")
        return method.flatten().mapNotNull { it as? CallInst }.filter { it.method == assertReachable && it.`class` == intrinsics }
    }

    fun getUnreachables(method: Method): List<Instruction> {
        val intrinsics = CM.getByName("$packageName/Intrinsics")
        val assertUnreachable = intrinsics.getMethod("assertUnreachable", "()V")
        return method.flatten().mapNotNull { it as? CallInst }.filter { it.method == assertUnreachable && it.`class` == intrinsics }
    }
}