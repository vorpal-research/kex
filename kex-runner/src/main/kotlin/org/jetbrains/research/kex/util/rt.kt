package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.JarContainer
import java.nio.file.Paths

fun getRuntime(): Container? {
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
