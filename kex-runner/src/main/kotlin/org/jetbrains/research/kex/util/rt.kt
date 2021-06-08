package org.jetbrains.research.kex.util

import com.beust.klaxon.Klaxon
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.ir.Class
import java.io.File
import java.net.URL
import java.nio.file.Paths

fun getRuntime(): Container? {
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "rt-${runtimeVersion}.jar"), Package.defaultPackage)
}

fun getKexRuntimePath(): URL? {
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val runtimeVersion = kexConfig.getStringValue("kex", "rtVersion") ?: return null
    return Paths.get(runtimePath, "kex-rt-${runtimeVersion}.jar").toUri().toURL()
}

fun getIntrinsics(): Container? {
    val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return null
    val intrinsicsVersion = kexConfig.getStringValue("kex", "intrinsicsVersion") ?: return null
    return JarContainer(Paths.get(runtimePath, "kex-intrinsics-${intrinsicsVersion}.jar"), Package.defaultPackage)
}

fun getPathSeparator(): String = System.getProperty("path.separator")

class KexRuntimeManager {
    private val mappings: Map<String, String> by lazy { load() }

    companion object {
        private data class Mapping(
            val original: String,
            val kex: String
        )

        fun load(): Map<String, String> {
            val runtimePath = kexConfig.getStringValue("kex", "libPath") ?: return mapOf()
            val mappingsFile = kexConfig.getStringValue("kex", "rt-mappings", "kex-rt-mappings.json")
            return Klaxon().parseArray<Mapping>(File(runtimePath, mappingsFile).readText())
                ?.associate { it.original to it.kex } ?: mapOf()
        }
    }

    operator fun get(klass: String) = mappings[klass]
    fun getValue(klass: String) = mappings[klass]

    operator fun get(klass: Class) = this[klass.fullName]?.let { klass.cm[it] }
}