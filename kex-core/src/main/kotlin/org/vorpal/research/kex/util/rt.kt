@file:Suppress("unused")

package org.vorpal.research.kex.util

import org.vorpal.research.kex.config.Config
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.JarContainer
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.io.path.readLines

val Config.outputDirectory: Path get() = getPathValue("kex", "outputDir")!!.normalize()

@Deprecated("kex now does not write instrumented code into a directory")
val Config.instrumentedCodeDirectory: Path
    get() {
        val instrumentedDirName = getStringValue("output", "instrumentedDir", "instrumented")
        val instrumentedCodeDir = outputDirectory.resolve(instrumentedDirName).toAbsolutePath()
        if (!getBooleanValue("debug", "saveInstrumentedCode", false)) {
            deleteOnExit(instrumentedCodeDir)
        }
        return instrumentedCodeDir.normalize()
    }

val Config.compiledCodeDirectory: Path
    get() {
        val compiledCodeDirName = getStringValue("compile", "compileDir", "compiled")
        val compiledCodeDir = outputDirectory.resolve(compiledCodeDirName).toAbsolutePath()
        if (!getBooleanValue("debug", "saveCompiledCode", false)) {
            deleteOnExit(compiledCodeDir)
        }
        return compiledCodeDir.normalize()
    }

val Config.testcaseDirectory: Path
    get() {
        val testcaseDirName = getPathValue("testGen", "testsDir", "tests")
        return outputDirectory.resolve(testcaseDirName).toAbsolutePath().normalize()
    }

val Config.runtimeDepsPath: Path?
    get() = getPathValue("kex", "runtimeDepsPath")?.normalize()

val Config.libPath: Path?
    get() = getStringValue("kex", "libPath")?.let {
        runtimeDepsPath?.resolve(it)?.normalize()
    }

fun getRuntime(): Container? {
    if (!kexConfig.getBooleanValue("kex", "useJavaRuntime", true)) return null
    val libPath = kexConfig.libPath ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return JarContainer(libPath.resolve("rt-${runtimeVersion}.jar"), Package.defaultPackage)
}

fun getIntrinsics(): Container? {
    val libPath = kexConfig.libPath ?: return null
    val intrinsicsVersion = kexConfig.getStringValue("kex", "intrinsicsVersion") ?: return null
    return JarContainer(libPath.resolve("kex-intrinsics-${intrinsicsVersion}.jar"), Package.defaultPackage)
}

fun getPathSeparator(): String = System.getProperty("path.separator")

fun getJunit(): Container? {
    val libPath = kexConfig.libPath ?: return null
    val junitVersion = kexConfig.getStringValue("kex", "junitVersion") ?: return null
    return JarContainer(libPath.resolve("junit-$junitVersion.jar").toAbsolutePath(), Package.defaultPackage)
}

fun getKexRuntime(): Container? {
    if (!kexConfig.getBooleanValue("kex", "useKexRuntime", true)) return null
    val libPath = kexConfig.libPath ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "kexRtVersion") ?: return null
    return JarContainer(libPath.resolve("kex-rt-${runtimeVersion}.jar"), Package.defaultPackage)
}

fun getJvmVersion(): Int {
    val versionStr = System.getProperty("java.version")
    return """(1.)?(\d+)""".toRegex().find(versionStr)?.let {
        it.groupValues[2].toInt()
    } ?: unreachable { log.error("Could not detect JVM version: \"{}\"", versionStr) }
}

fun getJvmModuleParams(): List<String> = when (getJvmVersion()) {
    in 1..7 -> unreachable { log.error("Unsupported version of JVM: ${getJvmVersion()}") }
    8 -> emptyList()
    else -> buildList {
        val modules = kexConfig.runtimeDepsPath?.resolve("modules.info")?.readLines().orEmpty()
        for (module in modules) {
            add("--add-opens")
            add(module)
        }
        add("--illegal-access=warn")
    }
}
