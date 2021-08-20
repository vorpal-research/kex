package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.util.getIntrinsics
import org.jetbrains.research.kex.util.getPathSeparator
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Flags
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path


@OptIn(ExperimentalStdlibApi::class)
abstract class KexTest(
    private val initTR: Boolean = false,
    private val initIntrinsics: Boolean = false,
    additionJarName: String? = null
) {
    val classPath: String = System.getProperty("java.class.path")
    val packageName = "org.jetbrains.research.kex.test"
    val `package` = Package.parse("$packageName.*")
    val jarPath: String
    val cm: ClassManager
    val loader: ClassLoader
    val additionContainers = mutableListOf<Container>()
    val packages = mutableListOf(`package`)

    init {
        val rootDir = System.getProperty("root.dir") ?: System.getProperty("user.dir")
        val version = System.getProperty("project.version")
        kexConfig.initialize(RuntimeConfig, FileConfig("$rootDir/kex-test.ini"))
        kexConfig.initLog("kex-test.log")

        jarPath = "$rootDir/kex-test/target/kex-test-$version-jar-with-dependencies.jar"
        val jar = Paths.get(jarPath).asContainer(`package`)!!
        loader = jar.classLoader
        cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        val additionJarContainer = jarContainerFromClasspathOrNull(additionJarName)
        val containersToInit = buildList {
            add(jar)
            if (initTR) add(getRuntime()!!)
            if (initIntrinsics) add(getIntrinsics()!!)
            if (additionJarContainer != null) {
                additionContainers.add(additionJarContainer)
                packages.add(additionJarContainer.commonPackage)
                add(additionJarContainer)
            }
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

    private fun jarContainerFromClasspathOrNull(name: String?): JarContainer? {
        name ?: return null
        val path = classPath.split(getPathSeparator()).lastOrNull { it.contains(name) } ?: return null
        return JarContainer(Path(path))
    }
}