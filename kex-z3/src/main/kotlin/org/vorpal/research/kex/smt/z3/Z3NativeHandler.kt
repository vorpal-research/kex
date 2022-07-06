package org.vorpal.research.kex.smt.z3

import org.vorpal.research.kex.util.deleteDirectory
import org.vorpal.research.kex.util.lowercased
import org.vorpal.research.kex.util.unzipArchive
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull

abstract class Z3NativeHandler {
    companion object {
        const val Z3_VERSION = "4.8.17"
        private val libraries = listOf("libz3", "libz3java")
        private val supportedArchs = setOf("amd64", "x86_64")
        internal val initializeCallback by lazy {
            System.setProperty("z3.skipLibraryLoad", "true")

            val arch = System.getProperty("os.arch")
            require(arch in supportedArchs) { "Not supported arch: $arch" }

            val osProperty = System.getProperty("os.name").lowercased()
            val (zipName, libraryNames) = when {
                osProperty.startsWith("linux") -> "z3-$Z3_VERSION-linux64-native.zip" to libraries.map { "$it.so" }
                else -> unreachable { log.error("Unknown OS: $osProperty") }
            }

            val tempDir = unzipArchive(
                Z3NativeHandler::class.java.classLoader.getResourceAsStream(zipName)
                    ?: unreachable { log.error("z3 archive not found") },
                "lib"
            )

            val resolvedLibraries = libraryNames.map { tempDir.resolve(it).toAbsolutePath().toString() }
            resolvedLibraries.forEach { System.load(it) }

            tryOrNull { deleteDirectory(tempDir) }
        }
    }

    init {
        initializeCallback
    }
}
