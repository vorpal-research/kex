package org.jetbrains.research.kex.jacoco

import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.ICoverageNode
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore
import org.jacoco.core.instr.Instrumenter
import org.jacoco.core.internal.analysis.PackageCoverageImpl
import org.jacoco.core.runtime.LoggerRuntime
import org.jacoco.core.runtime.RuntimeData
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.jacoco.TestsCompiler.CompiledClassLoader
import org.jetbrains.research.kex.launcher.AnalysisLevel
import org.jetbrains.research.kex.launcher.ClassLevel
import org.jetbrains.research.kex.launcher.MethodLevel
import org.jetbrains.research.kex.launcher.PackageLevel
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.isClass
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import org.junit.runner.Computer
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import org.junit.runner.notification.RunListener
import java.net.URLClassLoader
import java.util.jar.JarFile

interface CoverageInfo {
    val covered: Int
    val total: Int
    val ratio: Double
}

enum class CoverageUnit(unit: String) {
    INSTRUCTION("instructions"),
    BRANCH("branches"),
    LINE("lines"),
    COMPLEXITY("complexity");

    companion object {
        fun parse(unit: String) = when (unit) {
            INSTRUCTION.unitName -> INSTRUCTION
            BRANCH.unitName -> BRANCH
            LINE.unitName -> LINE
            COMPLEXITY.unitName -> COMPLEXITY
            else -> unreachable { log.error("Unknown coverage unit $unit") }
        }
    }

    val unitName: String = unit

    override fun toString(): String {
        return unitName
    }
}

enum class AnalysisUnit(unit: String) {
    METHOD("method"),
    CLASS("class"),
    PACKAGE("package");

    companion object {
        fun parse(unit: String) = when (unit) {
            METHOD.unitName -> METHOD
            CLASS.unitName -> CLASS
            PACKAGE.unitName -> PACKAGE
            else -> unreachable { log.error("Unknown analysis unit $unit") }
        }
    }

    val unitName: String = unit

    override fun toString(): String {
        return unitName
    }
}

data class GenericCoverageInfo(
    override val covered: Int,
    override val total: Int,
    val unit: CoverageUnit
) : CoverageInfo {
    override val ratio: Double get() = covered.toDouble() / total
    override fun toString(): String = buildString {
        append(String.format("%s of %s %s covered", covered, total, unit))
        if (total > 0) {
            append(String.format(" = %.2f", ratio * 100))
            append("%")
        }
    }
}

abstract class CommonCoverageInfo(
    val name: String,
    val level: AnalysisUnit,
    val instructionCoverage: CoverageInfo,
    val branchCoverage: CoverageInfo,
    val linesCoverage: CoverageInfo,
    val complexityCoverage: CoverageInfo
) {
    open fun print(detailed: Boolean = false) = toString()

    override fun toString(): String = buildString {
        appendLine(String.format("Coverage of %s %s:", name, level))
        appendLine("    $instructionCoverage")
        appendLine("    $branchCoverage")
        appendLine("    $linesCoverage")
        append("    $complexityCoverage")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommonCoverageInfo) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

class MethodCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo
) : CommonCoverageInfo(
    name,
    AnalysisUnit.METHOD,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage
)

class ClassCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo,
) : CommonCoverageInfo(
    name,
    AnalysisUnit.CLASS,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage
) {
    val methods = mutableSetOf<MethodCoverageInfo>()

    override fun print(detailed: Boolean) = buildString {
        appendLine(this@ClassCoverageInfo.toString())
        if (detailed) {
            methods.forEach {
                appendLine()
                appendLine(it.print(detailed))
            }
        }
    }
}

class PackageCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo
) : CommonCoverageInfo(
    name,
    AnalysisUnit.PACKAGE,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage
) {
    val classes = mutableSetOf<ClassCoverageInfo>()

    override fun print(detailed: Boolean) = buildString {
        if (detailed) {
            classes.forEach {
                appendLine(it.print(detailed))
            }
        }
        appendLine(this@PackageCoverageInfo.toString())
    }
}

class CoverageReporter(
    urlClassLoader: URLClassLoader
) {
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

    fun execute(analysisLevel: AnalysisLevel): CommonCoverageInfo {
        val coverageBuilder: CoverageBuilder
        val result = when (analysisLevel) {
            is PackageLevel -> {
                val urls = compiledClassLoader.urLs
                val jarPath = urls[urls.size - 1].toString().replace("file:", "")
                val jarFile = JarFile(jarPath)
                val jarEntries = jarFile.entries()
                val classes = mutableListOf<String>()
                while (jarEntries.hasMoreElements()) {
                    val jarEntry = jarEntries.nextElement()
                    if (analysisLevel.pkg.isParent(jarEntry.name) && jarEntry.isClass) {
                        classes.add(jarEntry.name)
                    }
                }
                coverageBuilder = getCoverageBuilder(classes)
                getPackageCoverage(analysisLevel.pkg, coverageBuilder)
            }
            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName
                coverageBuilder = getCoverageBuilder(listOf("$klass.class"))
                getClassCoverage(coverageBuilder).first()
            }
            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName
                coverageBuilder = getCoverageBuilder(listOf("$klass.class"))
                getMethodCoverage(coverageBuilder, method.name)!!
            }
        }
        return result
    }

    class TestLogger : RunListener() {
        override fun testRunFinished(result: Result) {
            log.info(buildString {
                appendLine("Result:")
                appendLine("run count ${result.runCount}")
                appendLine("ignore count ${result.ignoreCount}")
                appendLine("fail count ${result.failureCount}")
                appendLine("failures: ${result.failures.joinToString("\n")}")
            })
        }
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
            log.debug("Running test $testName")
            val jc = JUnitCore()
//            jc.addListener(TestLogger())
            jc.run(Computer(), testClass)
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
            tryOrNull { analyzer.analyzeClass(original, className.fullyQualifiedName) }
            original.close()
        }
        return coverageBuilder
    }

    private val String.fullyQualifiedName: String
        get() = removeSuffix(".class").replace('/', '.')

    private fun getClassCoverage(coverageBuilder: CoverageBuilder): Set<ClassCoverageInfo> =
        coverageBuilder.classes.map {
            val classCov = getCommonCounters<ClassCoverageInfo>(it.name, it)
            for (mc in it.methods) {
                classCov.methods += getCommonCounters<MethodCoverageInfo>(mc.name, mc)
            }
            classCov
        }.toSet()

    private fun getMethodCoverage(coverageBuilder: CoverageBuilder, method: String): CommonCoverageInfo? {
        for (mc in coverageBuilder.classes.iterator().next().methods) {
            if (mc.name == method) {
                return getCommonCounters<MethodCoverageInfo>(method, mc)
            }
        }
        return null
    }

    private fun getPackageCoverage(
        pkg: Package,
        coverageBuilder: CoverageBuilder
    ): PackageCoverageInfo {
        val pc = PackageCoverageImpl(pkg.canonicalName, coverageBuilder.classes, coverageBuilder.sourceFiles)
        val packCov = getCommonCounters<PackageCoverageInfo>(pkg.canonicalName, pc)
        packCov.classes.addAll(getClassCoverage(coverageBuilder))
        return packCov
    }

    private fun getCounter(unit: CoverageUnit, counter: ICounter): CoverageInfo {
        val covered = counter.coveredCount
        val total = counter.totalCount
        return GenericCoverageInfo(covered, total, unit)
    }

    private inline fun <reified T : CommonCoverageInfo> getCommonCounters(name: String, coverage: ICoverageNode): T {
        val insts = getCounter(CoverageUnit.INSTRUCTION, coverage.instructionCounter)
        val brs = getCounter(CoverageUnit.BRANCH, coverage.branchCounter)
        val lines = getCounter(CoverageUnit.LINE, coverage.lineCounter)
        val complexities = getCounter(CoverageUnit.COMPLEXITY, coverage.complexityCounter)

        return when (T::class.java) {
            MethodCoverageInfo::class.java -> MethodCoverageInfo(name, insts, brs, lines, complexities)
            ClassCoverageInfo::class.java -> ClassCoverageInfo(name, insts, brs, lines, complexities)
            PackageCoverageInfo::class.java -> PackageCoverageInfo(name, insts, brs, lines, complexities)
            else -> unreachable { log.error("Unknown common coverage info class ${T::class.java}") }
        } as T
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