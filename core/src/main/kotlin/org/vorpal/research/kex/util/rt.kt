package org.vorpal.research.kex.util

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.JarContainer
import java.nio.file.Paths

fun getRuntime(): Container? {
    if (!kexConfig.getBooleanValue("kex", "useJavaRuntime", true)) return null
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "rt-${runtimeVersion}.jar"), Package.defaultPackage)
}

fun getIntrinsics(): Container? {
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val intrinsicsVersion = kexConfig.getStringValue("kex", "intrinsicsVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "kex-intrinsics-${intrinsicsVersion}.jar"), Package.defaultPackage)
}

fun getPathSeparator(): String = System.getProperty("path.separator")

fun getJunit(): Container? {
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val junitVersion = kexConfig.getStringValue("kex", "junitVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "junit-$junitVersion.jar").toAbsolutePath(), Package.defaultPackage)
}

fun getKexRuntime(): Container? {
    if (!kexConfig.getBooleanValue("kex", "useKexRuntime", true)) return null
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "kexRtVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "kex-rt-${runtimeVersion}.jar"), Package.defaultPackage)
}