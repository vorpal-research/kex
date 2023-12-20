package org.vorpal.research.kex.util

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.util.toByteArray
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes

class KfgClassLoader(
    val cm: ClassManager,
    val paths: List<Path>,
    private val includes: Set<KfgTargetFilter> = INCLUDES,
    private val excludes: Set<KfgTargetFilter> = EXCLUDES,
    val transformation: (ConcreteClass) -> Unit = {}
) : ClassLoader() {
    private val cache = hashMapOf<String, Class<*>>()
    val fallback = PathClassLoader(paths)

    companion object {
        private val INCLUDES = setOf(
            "package org.vorpal.research.kex.test.*",
        ).mapTo(mutableSetOf()) { KfgTargetFilter.parse(it) }
        private val EXCLUDES = setOf(
            "package java.*",
            "package org.vorpal.research.kex.*",
        ).mapTo(mutableSetOf()) { KfgTargetFilter.parse(it) }
    }

    private fun readClassFromJar(name: String, path: Path): ByteArray? {
        val fileName = name.asmString + ".class"
        val jarFile = JarFile(path.toFile())
        val entry = jarFile.getJarEntry(fileName) ?: return null
        return jarFile.getInputStream(entry).readBytes()
    }

    private fun readClassFromDirectory(name: String, path: Path): ByteArray? {
        val fileName = name.asmString + ".class"
        val resolved = path.resolve(fileName)
        return when {
            resolved.exists() -> resolved.readBytes()
            else -> null
        }
    }

    private fun defineClass(name: String, bytes: ByteArray): Class<*> {
        val klass = defineClass(name, bytes, 0, bytes.size)
        cache[name] = klass
        return klass
    }

    private fun loadClassFromClassPath(name: String): Class<*> {
        for (path in paths) {
            val bytes = when {
                path.isDirectory() -> readClassFromDirectory(name, path)
                path.fileName.toString().endsWith(".jar") -> readClassFromJar(name, path)
                else -> null
            }
            if (bytes != null) {
                return defineClass(name, bytes)
            }
        }
        return parent?.loadClass(name) ?: throw ClassNotFoundException()
    }

    private fun loadFromKfg(name: String): Class<*> = when (val kfgClass = cm[name.asmString]) {
        is ConcreteClass -> {
            transformation(kfgClass)
            tryOrNull {
                kfgClass.toByteArray(fallback)
            }?.let {
                defineClass(name, it)
            } ?: loadClassFromClassPath(name)
        }

        else -> loadClassFromClassPath(name)
    }

    override fun loadClass(name: String): Class<*> {
        @Suppress("NAME_SHADOWING") val name = name.javaString
        val asmName = name.asmString
        return synchronized(this.getClassLoadingLock(name)) {
            when {
                name in cache -> cache[name]!!
                includes.any { it.matches(asmName) } -> loadFromKfg(name)
                excludes.any { it.matches(asmName) } -> loadClassFromClassPath(name)
                else -> loadFromKfg(name)
            }
        }
    }
}
