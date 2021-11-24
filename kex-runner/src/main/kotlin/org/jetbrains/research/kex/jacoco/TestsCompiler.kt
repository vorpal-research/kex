package org.jetbrains.research.kex.jacoco

import org.jetbrains.research.kthelper.tryOrNull
import java.io.*
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import javax.tools.*
import kotlin.io.path.isRegularFile

class TestsCompiler(private val outputDir: Path) {
    private val generatedClasses: MutableList<ClassJavaFileObject> = ArrayList()

    private fun getGeneratedClasses(javaFile: File): List<ClassJavaFileObject> {
        val compiler = ToolProvider.getSystemJavaCompiler()

        val standardFileManager = compiler.getStandardFileManager(null, null, null)
        standardFileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(outputDir.toFile()))
        val fileManager = SimpleJavaFileManager(
            standardFileManager
        )
        val compilationUnit: JavaFileObject = TestJavaFileObject(javaFile)
        val compilerOutput = ByteArrayOutputStream()
        val compilationTask = compiler.getTask(
            compilerOutput.writer(), fileManager, null, null, null, listOf(compilationUnit)
        )
        compilationTask.call()
        return fileManager.generatedOutputFiles
    }

    fun generateAll(directoryPath: Path) = tryOrNull {
        Files.walk(directoryPath)
            .filter { it.isRegularFile() }
            .map { it.toFile() }
            .map { getGeneratedClasses(it) }
            .forEach { generatedClasses.addAll(it) }
    }

    val testsNames: List<String>
        get() = generatedClasses
            .stream()
            .map { obj: ClassJavaFileObject -> obj.className }
            .collect(Collectors.toList())

    fun getCompiledClassLoader(urlClassLoader: URLClassLoader): CompiledClassLoader {
        return CompiledClassLoader(generatedClasses, urlClassLoader)
    }

    class TestJavaFileObject(
        private val javaFile: File
    ) : SimpleJavaFileObject(
        URI.create(javaFile.name),
        JavaFileObject.Kind.SOURCE
    ) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
            Files.readAllLines(javaFile.toPath()).joinToString("\n")
    }

    class ClassJavaFileObject(
        val className: String
    ) : SimpleJavaFileObject(
        URI.create(className.replace('.', '/') + ".class"),
        JavaFileObject.Kind.CLASS
    ) {
        private val outputStream = ByteArrayOutputStream()

        val bytes: ByteArray
            get() = outputStream.toByteArray()

        override fun openOutputStream(): OutputStream = outputStream
    }

    private class SimpleJavaFileManager(
        fileManager: JavaFileManager
    ) : ForwardingJavaFileManager<JavaFileManager>(fileManager) {
        private val outputFiles = mutableListOf<ClassJavaFileObject>()

        val generatedOutputFiles: List<ClassJavaFileObject>
            get() = outputFiles

        override fun getJavaFileForOutput(
            location: JavaFileManager.Location,
            className: String,
            kind: JavaFileObject.Kind,
            sibling: FileObject
        ): JavaFileObject = ClassJavaFileObject(className).also { outputFiles.add(it) }
    }

    class CompiledClassLoader(
        val files: List<ClassJavaFileObject>,
        val parent: URLClassLoader
    ) : ClassLoader() {
        val urLs: Array<URL>
            get() = parent.urLs

        override fun loadClass(name: String): Class<*> {
            val bytes = getBytes(name)
            return if (bytes != null) {
                defineClass(name, bytes, 0, bytes.size)
            } else parent.loadClass(name)
        }

        fun getBytes(name: String): ByteArray? {
            for (file in files) {
                if (file.className == name) {
                    return file.bytes
                }
            }
            return null
        }

        override fun getResourceAsStream(name: String): InputStream {
            return parent.getResourceAsStream(name)!!
        }

    }
}