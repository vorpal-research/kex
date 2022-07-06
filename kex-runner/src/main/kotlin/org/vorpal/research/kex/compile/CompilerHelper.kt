package org.vorpal.research.kex.compile

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.getJunit
import java.nio.file.Path

class CompilerHelper(val ctx: ExecutionContext) {
    private val junitJar = getJunit()!!
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    val compileDir: Path = outputDir.resolve(
        kexConfig.getPathValue("compile", "compileDir", "compiled/")
    ).also {
        it.toFile().mkdirs()
    }

    fun compileFile(file: Path) {
        val compilerDriver = JavaCompilerDriver(
            listOf(*ctx.classPath.toTypedArray(), junitJar.path), compileDir
        )
        compilerDriver.compile(listOf(file))
    }
}