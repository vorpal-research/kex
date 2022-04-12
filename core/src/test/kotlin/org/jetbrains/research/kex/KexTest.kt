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
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Flags
import java.nio.file.Paths

abstract class KexTest {
    val packageName = "org.jetbrains.research.kex.test"
    val `package` = Package.parse("$packageName.*")
    val jarPath: String
    val jar: Container
    val cm: ClassManager
    val loader: ClassLoader

    init {
        val rootDir = System.getProperty("root.dir")
        val version = System.getProperty("project.version")
        kexConfig.initialize(RuntimeConfig, FileConfig("$rootDir/kex-test.ini"))
        kexConfig.initLog("kex-test.log")

        jarPath = "$rootDir/kex-test/target/kex-test-$version-jar-with-dependencies.jar"
        jar = Paths.get(jarPath).asContainer(`package`)!!

        val jars = listOfNotNull(jar, getRuntime(), getIntrinsics())
        loader = jars.first().classLoader
        cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        cm.initialize(*jars.toTypedArray())
    }

    protected fun getPSA(method: Method): PredicateStateAnalysis {
        if (method.hasLoops) {
            LoopSimplifier(cm).visit(method)
            LoopDeroller(cm).visit(method)
        }

        val psa = PredicateStateAnalysis(cm)
        psa.visit(method)
        return psa
    }
}