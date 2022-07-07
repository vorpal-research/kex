package org.vorpal.research.kex.util

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.JarContainer
import java.nio.file.Path

fun getRuntimeDepsPath(): Path? =
    kexConfig.getPathValue("kex", "runtimeDepsPath")

fun getRuntime(): Container? {
    if (!kexConfig.getBooleanValue("kex", "useJavaRuntime", true)) return null
    val runtimeDepsPath = getRuntimeDepsPath() ?: return null
    val libPath = runtimeDepsPath.resolve(kexConfig.getStringValue("kex", "libPath") ?: return null)
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return JarContainer(libPath.resolve("rt-${runtimeVersion}.jar"), Package.defaultPackage)
}

fun getIntrinsics(): Container? {
    val runtimeDepsPath = getRuntimeDepsPath() ?: return null
    val libPath = runtimeDepsPath.resolve(kexConfig.getStringValue("kex", "libPath") ?: return null)
    val intrinsicsVersion = kexConfig.getStringValue("kex", "intrinsicsVersion") ?: return null
    return JarContainer(libPath.resolve("kex-intrinsics-${intrinsicsVersion}.jar"), Package.defaultPackage)
}

fun getPathSeparator(): String = System.getProperty("path.separator")

fun getJunit(): Container? {
    val runtimeDepsPath = getRuntimeDepsPath() ?: return null
    val libPath = runtimeDepsPath.resolve(kexConfig.getStringValue("kex", "libPath") ?: return null)
    val junitVersion = kexConfig.getStringValue("kex", "junitVersion") ?: return null
    return JarContainer(libPath.resolve("junit-$junitVersion.jar").toAbsolutePath(), Package.defaultPackage)
}

fun getKexRuntime(): Container? {
    if (!kexConfig.getBooleanValue("kex", "useKexRuntime", true)) return null
    val runtimeDepsPath = getRuntimeDepsPath() ?: return null
    val libPath = runtimeDepsPath.resolve(kexConfig.getStringValue("kex", "libPath") ?: return null)
    val runtimeVersion = kexConfig.getStringValue("kex", "kexRtVersion") ?: return null
    return JarContainer(libPath.resolve("kex-rt-${runtimeVersion}.jar"), Package.defaultPackage)
}
