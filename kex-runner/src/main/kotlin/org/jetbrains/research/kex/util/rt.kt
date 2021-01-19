package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.container.JarContainer
import java.nio.file.Paths

fun getRuntime(): JarContainer? {
    val runtimePath = kexConfig.getStringValue("kex", "rtPath") ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "rt-${runtimeVersion}.jar"), Package.defaultPackage)
}
