package org.vorpal.research.kex.util

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kthelper.logging.info
import org.vorpal.research.kthelper.logging.warn
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes

class PathClassLoader(
    val paths: List<Path>,
    parent: ClassLoader = PathClassLoader::class.java.classLoader
) : ClassLoader(parent) {
    private val cache = hashMapOf<String, Class<*>>()

    init {
        val isJava8 = tryOrNull { System.getProperty("java.version") }?.startsWith("1.8") != false
        if (kexConfig.isMockingEnabled && kexConfig.isMockitoJava8WorkaroundEnabled && isJava8) {
            org.vorpal.research.kthelper.logging.log.info { "Applying workaround for mockito on java 8" }
            if (applyJava8Workaround()) {
                org.vorpal.research.kthelper.logging.log.info { "Workaround successfully applied" }
            } else {
                org.vorpal.research.kthelper.logging.log.warn { "Workaround failed" }
            }
        }
    }

    private fun applyJava8Workaround(): Boolean = tryOrNull {
        definePackage("org.mockito.codegen", "", "", "", "", "", "", null)
    } != null

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

    override fun loadClass(name: String): Class<*> = synchronized(this.getClassLoadingLock(name)) {
        if (name in cache) return cache[name]!!
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
}
