package org.jetbrains.research.kex.compile

import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider


abstract class CompilerDriver(val classPath: List<Path>, val outputDir: Path) {
    abstract fun compile(sources: List<Path>): List<Path>
}


class JavaCompilerDriver(classPath: List<Path>, outputDir: Path) : CompilerDriver(classPath, outputDir) {
    private val compiler = ToolProvider.getSystemJavaCompiler()

    override fun compile(sources: List<Path>): List<Path> {
        val fileManager = compiler.getStandardFileManager(null, null, null)
        fileManager.setLocation(StandardLocation.CLASS_PATH, classPath.map { it.toFile() })
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(outputDir.toFile()))
        val objects = fileManager.getJavaFileObjectsFromFiles(sources.map { it.toFile() })
        val task = compiler.getTask(null, fileManager, null, null, null, objects)
        val result = mutableListOf<Path>()
        if (task.call()) {
            for (jfo in fileManager.list(
                StandardLocation.CLASS_OUTPUT,
                "", Collections.singleton(JavaFileObject.Kind.CLASS), true
            )) {
                result.add(Paths.get(jfo.name).toAbsolutePath())
            }
        }
        return result
    }
}

class KotlinCompilerDriver(classPath: List<Path>, outputDir: Path) : CompilerDriver(classPath, outputDir) {
    private val kotlinc = "kotlinc"
    override fun compile(sources: List<Path>): List<Path> {
        TODO("Not yet implemented")
    }
}