package org.jetbrains.research.kex.compile

import org.jetbrains.research.kthelper.KtException
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.logging.log
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider


abstract class CompilerDriver(val classPath: List<Path>, val outputDir: Path) {
    abstract fun compile(sources: List<Path>): List<Path>
}

class CompilationException : KtException {
    constructor(message: String = "") : super(message)
    constructor(message: String = "", throwable: Throwable) : super(message, throwable)
}

class JavaCompilerDriver(classPath: List<Path>, outputDir: Path) : CompilerDriver(classPath, outputDir) {
    private val compiler = ToolProvider.getSystemJavaCompiler()

    override fun compile(sources: List<Path>): List<Path> {
        val fileManager = compiler.getStandardFileManager(null, null, null)
        fileManager.setLocation(StandardLocation.CLASS_PATH, classPath.map { it.toFile() })
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(outputDir.toFile()))
        val objects = fileManager.getJavaFileObjectsFromFiles(sources.map { it.toFile() })

        val compilerOutput = ByteArrayOutputStream()
        val task = compiler.getTask(compilerOutput.writer(), fileManager, null, listOf("-Xlint:none", "-Xlint:unchecked"), null, objects)
        val compileSuccess = `try` { task.call() }.getOrElse { false }
        if (!compileSuccess) {
            log.error("Task $task failed")
            log.error("Sources: ${sources.joinToString("\n", prefix = "\n")}")
            log.error(compilerOutput.toString())
            throw CompilationException()
        }

        return fileManager.list(
            StandardLocation.CLASS_OUTPUT,
            "", Collections.singleton(JavaFileObject.Kind.CLASS), true
        ).map { Paths.get(it.name).toAbsolutePath() }
    }
}

class KotlinCompilerDriver(classPath: List<Path>, outputDir: Path) : CompilerDriver(classPath, outputDir) {
    private val kotlinc = "kotlinc"
    override fun compile(sources: List<Path>): List<Path> {
        TODO("Not yet implemented")
    }
}