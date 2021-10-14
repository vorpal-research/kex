package org.jetbrains.research.kex.jacoco

import org.jacoco.core.analysis.*
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore
import org.jacoco.core.instr.Instrumenter
import org.jacoco.core.internal.analysis.PackageCoverageImpl
import org.jacoco.core.runtime.LoggerRuntime
import org.jacoco.core.runtime.RuntimeData
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.jacoco.TestsCompiler.CompiledClassLoader
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.isClass
import org.jetbrains.research.kthelper.logging.log
import org.junit.runner.JUnitCore
import java.net.URLClassLoader
import java.util.jar.JarFile

class CoverageReporter(private val pkg: Package, urlClassLoader: URLClassLoader) {
    private val compiledClassLoader: CompiledClassLoader
    private val instrAndTestsClassLoader: MemoryClassLoader
    private val tests: List<String>
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    private val testsDir = outputDir.resolve(
        kexConfig.getPathValue("compile", "compileDir", "tests/")
    ).also {
        it.toFile().mkdirs()
    }
    private val compileDir = outputDir.resolve(
        kexConfig.getPathValue("compile", "compileDir", "compiled/")
    ).also {
        it.toFile().mkdirs()
    }

    init {
        val testsCompiler = TestsCompiler(compileDir)
        testsCompiler.generateAll(testsDir)
        compiledClassLoader = testsCompiler.getCompiledClassLoader(urlClassLoader)
        instrAndTestsClassLoader = MemoryClassLoader(compiledClassLoader)
        tests = testsCompiler.testsNames
    }

    fun execute(analysisLevel: String): String {
        val canonicalName = analysisLevel.replace("[()]|(CLASS|METHOD)".toRegex(), "")
        val coverageBuilder: CoverageBuilder
        val result: String
        when {
            canonicalName != analysisLevel -> {
                val pair = canonicalName.split(", ").toTypedArray()
                val klass = pair[0].replace("klass=", "")
                coverageBuilder = getCoverageBuilder(listOf("$klass.class"))
                result = if (analysisLevel.startsWith("CLASS")) {
                    getClassCoverage(coverageBuilder)
                } else {
                    val method = pair[1].replace("method=", "")
                    getMethodCoverage(coverageBuilder, method)
                }
            }
            else -> {
                val urls = compiledClassLoader.urLs
                val jarPath = urls[urls.size - 1].toString().replace("file:", "")
                val jarFile = JarFile(jarPath)
                val jarEntries = jarFile.entries()
                val classes = mutableListOf<String>()
                while (jarEntries.hasMoreElements()) {
                    val jarEntry = jarEntries.nextElement()
                    if (pkg.isParent(jarEntry.name) && jarEntry.isClass) {
                        classes.add(jarEntry.name)
                    }
                }
                coverageBuilder = getCoverageBuilder(classes)
                result = getPackageCoverage(coverageBuilder)
            }
        }
        return result
    }

    private fun getCoverageBuilder(classes: List<String>): CoverageBuilder {
        val runtime = LoggerRuntime()
        for (className in classes) {
            val original = compiledClassLoader.getResourceAsStream(className)
            val fullyQualifiedName = className.fullyQualifiedName
            val instr = Instrumenter(runtime)
            val instrumented = instr.instrument(original, fullyQualifiedName)
            original.close()
            instrAndTestsClassLoader.addDefinition(fullyQualifiedName, instrumented)
        }
        val data = RuntimeData()
        runtime.startup(data)

        log.debug("Running tests...")
        for (testName in tests) {
            instrAndTestsClassLoader.addDefinition(testName, compiledClassLoader.getBytes(testName)!!)
            val testClass = instrAndTestsClassLoader.loadClass(testName)
            JUnitCore.runClasses(testClass)
        }

        log.debug("Analyzing Coverage...")
        val executionData = ExecutionDataStore()
        val sessionInfos = SessionInfoStore()
        data.collect(executionData, sessionInfos, false)
        runtime.shutdown()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(executionData, coverageBuilder)
        for (className in classes) {
            val original = compiledClassLoader.getResourceAsStream(className)
            analyzer.analyzeClass(original, className.fullyQualifiedName)
            original.close()
        }
        return coverageBuilder
    }

    private val String.fullyQualifiedName: String
        get() = removeSuffix(".class").replace('/', '.')

    private fun getClassCoverage(coverageBuilder: CoverageBuilder): String = buildString {
        for (cc in coverageBuilder.classes) {
            append(getCommonCounters("class", cc.name, cc))
            appendLine(getCounter("methods", cc.methodCounter))
            for (mc in cc.methods) {
                appendLine(getCommonCounters("method", mc.name, mc))
            }
            appendLine()
        }
    }

    private fun getMethodCoverage(coverageBuilder: CoverageBuilder, method: String): String {
        for (mc in coverageBuilder.classes.iterator().next().methods) {
            if (mc.name == method) {
                return getCommonCounters("method", method, mc)
            }
        }
        return ""
    }

    private fun getPackageCoverage(coverageBuilder: CoverageBuilder): String {
        val pc = PackageCoverageImpl(pkg.canonicalName, coverageBuilder.classes, coverageBuilder.sourceFiles)
        return getCommonCounters("package", pkg.canonicalName, pc) +
                getCounter("methods", pc.methodCounter) +
                getCounter("classes", pc.classCounter) +
                getClassCoverage(coverageBuilder)
    }

    private fun getCounter(unit: String, counter: ICounter): String {
        val covered = counter.coveredCount
        val total = counter.totalCount
        var coverage = String.format("%s of %s %s covered", covered, total, unit)
        if (total != 0) {
            coverage += String.format(" = %.02f", covered.toFloat() / total * 100) + '%'
        }
        return coverage.trimIndent()
    }

    private fun getCommonCounters(level: String, name: String, coverage: ICoverageNode): String =
        String.format("Coverage of %s %s:%n", level, name) +
                getCounter("instructions", coverage.instructionCounter) +
                getCounter("branches", coverage.branchCounter) +
                getCounter("lines", coverage.lineCounter) +
                getCounter("complexity", coverage.complexityCounter)

    private class MemoryClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        private val definitions = mutableMapOf<String, ByteArray>()

        fun addDefinition(name: String, bytes: ByteArray) {
            definitions[name] = bytes
        }

        override fun loadClass(name: String): Class<*> {
            val bytes = definitions[name]
            return if (bytes != null) {
                defineClass(name, bytes, 0, bytes.size)
            } else parent.loadClass(name)
        }
    }
}