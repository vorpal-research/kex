package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.util.getIntrinsics
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Flags
import java.nio.file.Paths


@OptIn(ExperimentalStdlibApi::class)
abstract class KexTest(
    private val initTR: Boolean = false,
    private val initIntrinsics: Boolean = false
) {
    val packageName = "org.jetbrains.research.kex.test"
    val `package` = Package.parse("$packageName.*")
    val jarPath: String
    val cm: ClassManager
    val loader: ClassLoader

    init {
        val rootDir = System.getProperty("root.dir") ?: System.getProperty("user.dir")
        val version = System.getProperty("project.version")
        kexConfig.initialize(RuntimeConfig, FileConfig("$rootDir/kex-test.ini"))
        kexConfig.initLog("kex-test.log")

        jarPath = "$rootDir/kex-test/target/kex-test-$version-jar-with-dependencies.jar"
        val jar = Paths.get(jarPath).asContainer(`package`)!!
        loader = jar.classLoader
        cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        val containersToInit = buildList {
            add(jar)
            if (initTR) add(getRuntime()!!)
            if (initIntrinsics) add(getIntrinsics()!!)
        }.toTypedArray()
        cm.initialize(*containersToInit)
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
}