package org.vorpal.research.kex.smt.z3

import com.microsoft.z3.Native
import org.vorpal.research.kex.util.deleteOnExit
import org.vorpal.research.kex.util.lowercased
import org.vorpal.research.kex.util.unzipArchive
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import kotlin.io.path.listDirectoryEntries

abstract class Z3NativeLoader {
    companion object {
        const val Z3_VERSION = "4.8.17"
        private val libraries = listOf("libz3", "libz3java")
        private val vcWinLibrariesToLoadBefore = listOf("vcruntime140", "vcruntime140_1")
        private val supportedArchs = setOf("amd64", "x86_64")
        private val initializeCallback by lazy {
            System.setProperty("z3.skipLibraryLoad", "true")

            val arch = System.getProperty("os.arch")
            ktassert(arch in supportedArchs) { log.error("Not supported arch: $arch") }

            val osProperty = System.getProperty("os.name").lowercased()
            val (zipName, libraryNames) = when {
                osProperty.startsWith("linux") -> {
                    "z3-$Z3_VERSION-linux64-native.zip" to libraries.map { "$it.so" }
                }
                osProperty.startsWith("windows") -> {
                    "z3-$Z3_VERSION-win64-native.zip" to (vcWinLibrariesToLoadBefore + libraries).map { "$it.dll" }
                }
                osProperty.startsWith("mac") -> {
                    "z3-$Z3_VERSION-osx64-native.zip" to libraries.map { "$it.dylib" }
                }
                else -> unreachable { log.error("Unknown OS: $osProperty") }
            }

            val tempDir = unzipArchive(
                Z3NativeLoader::class.java.classLoader.getResourceAsStream(zipName)
                    ?: unreachable { log.error("z3 archive not found") },
                "lib"
            ).also {
                deleteOnExit(it)
            }.listDirectoryEntries().first()

            val resolvedLibraries = libraryNames.map { lib ->
                tempDir
                    .resolve("bin")
                    .resolve(lib)
                    .toAbsolutePath()
                    .toString()
            }
            resolvedLibraries.forEach { System.load(it) }
            setGlobalParams()
        }

        private fun setGlobalParams() {
            Native.globalParamSet("memory_max_size", "${8L * 1024 * 1024 * 1024}")
        }
    }

    init {
        initializeCallback
    }
}
