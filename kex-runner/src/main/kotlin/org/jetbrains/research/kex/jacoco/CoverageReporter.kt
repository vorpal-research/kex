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
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Class as KfgClass
import org.jetbrains.research.kfg.util.isClass
import org.jetbrains.research.kthelper.logging.log
import org.junit.runner.JUnitCore
import java.net.URLClassLoader
import java.util.jar.JarFile

sealed class CoverageLevel {
    object PackageLevel : CoverageLevel()
    data class ClassLevel(val klass: KfgClass) : CoverageLevel()
    data class MethodLevel(val method: Method) : CoverageLevel()
}

class CoverageReporter(private val pkg: Package, urlClassLoader: URLClassLoader) {
    private val compiledClassLoader: CompiledClassLoader
    private val instrAndTestsClassLoader: MemoryClassLoader
    private val tests: List<String>
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    private val testsDir = outputDir.resolve(
        kexConfig.getPathValue("compile", "testsDir", "tests/")
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

    fun execute(analysisLevel: CoverageLevel = CoverageLevel.PackageLevel): String {
        val coverageBuilder: CoverageBuilder
        val result = when (analysisLevel) {
            CoverageLevel.PackageLevel -> {
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
                getPackageCoverage(coverageBuilder)
            }
            is CoverageLevel.ClassLevel -> {
                val klass = analysisLevel.klass.fullName
                coverageBuilder = getCoverageBuilder(listOf("$klass.class"))
                getClassCoverage(coverageBuilder)
            }
            is CoverageLevel.MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName
                coverageBuilder = getCoverageBuilder(listOf("$klass.class"))
                getMethodCoverage(coverageBuilder, method.name)
            }
        }
        return result.trimIndent()
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
            appendLine(getCommonCounters("class", cc.name, cc))
            appendLine(getCounter("methods", cc.methodCounter))
            appendLine()
            for (mc in cc.methods) {
                appendLine(getCommonCounters("method", mc.name, mc))
            }
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
        return buildString {
            appendLine(getClassCoverage(coverageBuilder))
            appendLine()
            appendLine(getCommonCounters("package", pkg.canonicalName, pc))
            appendLine(getCounter("methods", pc.methodCounter))
            appendLine(getCounter("classes", pc.classCounter))
        }
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

    private fun getCommonCounters(level: String, name: String, coverage: ICoverageNode): String = buildString {
        appendLine(String.format("Coverage of %s %s:", level, name))
        appendLine(getCounter("  instructions", coverage.instructionCounter))
        appendLine(getCounter("  branches", coverage.branchCounter))
        appendLine(getCounter("  lines", coverage.lineCounter))
        appendLine(getCounter("  complexity", coverage.complexityCounter))
    }

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