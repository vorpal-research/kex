package org.vorpal.research.kex.compile

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.getJunit
import java.nio.file.Path

class CompilerHelper(val ctx: ExecutionContext) {
    private val junitJar = getJunit()!!
    private val compileDir: Path = kexConfig.compiledCodeDirectory.also {
        it.toFile().mkdirs()
    }

    fun compileFile(file: Path) {
        val compilerDriver = JavaCompilerDriver(
            listOf(*ctx.classPath.toTypedArray(), junitJar.path), compileDir
        )
        compilerDriver.compile(listOf(file))
    }
}