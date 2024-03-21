package org.vorpal.research.kex.compile

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.getJunit
import org.vorpal.research.kex.util.testcaseDirectory
import ru.spbstu.wheels.mapToArray
import java.nio.file.Path

class CompilerHelper(val ctx: ExecutionContext) {
    private val junitJar = getJunit()!!
    private val enabled: Boolean = kexConfig.getBooleanValue("compile", "enabled", true)
    private val compileDir: Path = kexConfig.compiledCodeDirectory.also {
        it.toFile().mkdirs()
    }
    private val testDirectory = kexConfig.testcaseDirectory

    fun compileFile(file: Path) {
        if (!enabled) return

        val compilerDriver = JavaCompilerDriver(
            listOf(*ctx.classPath.toTypedArray(), *getJunit().mapToArray { it.path }, testDirectory), compileDir
        )
        compilerDriver.compile(listOf(file))
    }
}
