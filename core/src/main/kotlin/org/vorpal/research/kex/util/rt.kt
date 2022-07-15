package org.vorpal.research.kex.util

import org.vorpal.research.kex.config.Config
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.JarContainer
import java.nio.file.Path

val Config.outputDirectory: Path get() = getPathValue("kex", "outputDir")!!

val Config.instrumentedCodeDirectory: Path
    get() {
        val instrumentedDirName = getStringValue("output", "instrumentedDir", "instrumented")
        val instrumentedCodeDir = outputDirectory.resolve(instrumentedDirName).toAbsolutePath()
        if (!getBooleanValue("debug", "saveInstrumentedCode", false)) {
            deleteOnExit(instrumentedCodeDir)
        }
        return instrumentedCodeDir
    }

val Config.compiledCodeDirectory: Path
    get() {
        val compiledCodeDirName = getStringValue("compile", "compileDir", "compiled")
        val compiledCodeDir = outputDirectory.resolve(compiledCodeDirName).toAbsolutePath()
        if (!getBooleanValue("debug", "saveCompiledCode", false)) {
            deleteOnExit(compiledCodeDir)
        }
        return compiledCodeDir
    }

val Config.runtimeDepsPath: Path?
    get() = getPathValue("kex", "runtimeDepsPath")

val Config.libPath: Path?
    get() = getStringValue("kex", "libPath")?.let {
        runtimeDepsPath?.resolve(it)
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
