package org.jetbrains.research.kex.util

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.Jar
import org.jetbrains.research.kfg.Package
import java.nio.file.Paths

fun getRuntime(): Jar? {
    val runtimePath = kexConfig.getStringValue("kex", "rtPath") ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return Jar(Paths.get(runtimePath, "rt-${runtimeVersion}.jar"), Package.defaultPackage)
}