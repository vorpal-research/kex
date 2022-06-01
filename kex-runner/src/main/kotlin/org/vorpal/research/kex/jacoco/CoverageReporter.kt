package org.vorpal.research.kex.jacoco

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
import org.junit.runner.Computer
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import org.junit.runner.notification.RunListener
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.TestsCompiler.CompiledClassLoader
import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kex.launcher.ClassLevel
import org.vorpal.research.kex.launcher.MethodLevel
import org.vorpal.research.kex.launcher.PackageLevel
import org.vorpal.research.kex.util.isFinal
import org.vorpal.research.kex.util.resolve
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.util.isClass
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.streams.toList

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
        appendLine(String.format("Coverage of `%s` %s:", name, level))
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
    containers: List<Container> = listOf()
) {
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    private val jacocoInstrumentedDir = outputDir.resolve(
        kexConfig.getPathValue("testGen", "jacocoDir", "jacoco/")
    ).also {
        it.toFile().mkdirs()
    }
    private val compileDir = outputDir.resolve(
        kexConfig.getPathValue("compile", "compileDir", "compiled/")
    ).also {
        it.toFile().mkdirs()
    }

    init {
        for (container in containers) {
            container.extract(jacocoInstrumentedDir)
        }
    }

    private val Path.isClass get() = name.endsWith(".class")

    fun execute(
        cm: ClassManager,
        analysisLevel: AnalysisLevel,
    ): CommonCoverageInfo {
        val coverageBuilder: CoverageBuilder
        val result = when (analysisLevel) {
            is PackageLevel -> {
                val a = Files.walk(jacocoInstrumentedDir).toList()
                val classes = Files.walk(jacocoInstrumentedDir)
                    .filter { it.isClass }
                    .filter {
                        analysisLevel.pkg.isParent(it.fullyQualifiedName(jacocoInstrumentedDir).replace('.', '/'))
                    }
                    .toList()
                coverageBuilder = getCoverageBuilder(classes)
                getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
            }
            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName
                coverageBuilder = getCoverageBuilder(listOf(jacocoInstrumentedDir.resolve("$klass.class")))
                getClassCoverage(cm, coverageBuilder).first()
            }
            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName
                coverageBuilder = getCoverageBuilder(listOf(jacocoInstrumentedDir.resolve("$klass.class")))
                getMethodCoverage(coverageBuilder, method)!!
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

    private fun getCoverageBuilder(classes: List<Path>): CoverageBuilder {
        val runtime = LoggerRuntime()
        for (classPath in classes) {
            val instrumented = classPath.inputStream().use {
                val fullyQualifiedName = classPath.fullyQualifiedName(jacocoInstrumentedDir)
                val instr = Instrumenter(runtime)
                instr.instrument(it, fullyQualifiedName)
            }
            classPath.writeBytes(instrumented)
        }
        val data = RuntimeData()
        runtime.startup(data)

        log.debug("Running tests...")
        for (testPath in Files.walk(compileDir).filter { it.isClass }) {
            val classLoader = URLClassLoader(arrayOf(jacocoInstrumentedDir.toUri().toURL(), compileDir.toUri().toURL()))
            val testClassName = testPath.fullyQualifiedName(compileDir)
            val testClass = classLoader.loadClass(testClassName)
            log.debug("Running test $testClassName")
            val jc = JUnitCore()
            if (kexConfig.getBooleanValue("testGen", "logJUnit", false)) {
                jc.addListener(TestLogger())
            }
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
            className.inputStream().use {
                tryOrNull { analyzer.analyzeClass(it, className.fullyQualifiedName(jacocoInstrumentedDir)) }
            }
        }
        return coverageBuilder
    }

    private val String.fullyQualifiedName: String
        get() = removeSuffix(".class").replace('/', '.')

    private fun Path.fullyQualifiedName(base: Path): String =
        relativeTo(base).toString().removePrefix("../").replace('/', '.').removeSuffix(".class")

    private fun getClassCoverage(
        cm: ClassManager,
        coverageBuilder: CoverageBuilder
    ): Set<ClassCoverageInfo> =
        coverageBuilder.classes.map {
            val kfgClass = cm[it.name]
            val classCov = getCommonCounters<ClassCoverageInfo>(it.name, it)
            for (mc in it.methods) {
                val kfgMethod = kfgClass.getMethod(mc.name, mc.desc)
                classCov.methods += getCommonCounters<MethodCoverageInfo>(kfgMethod.prototype.fullyQualifiedName, mc)
            }
            classCov
        }.toSet()

    private fun getMethodCoverage(coverageBuilder: CoverageBuilder, method: Method): CommonCoverageInfo? {
        for (mc in coverageBuilder.classes.iterator().next().methods) {
            if (mc.name == method.name && mc.desc == method.asmDesc) {
                return getCommonCounters<MethodCoverageInfo>(method.prototype.fullyQualifiedName, mc)
            }
        }
        return null
    }

    private fun getPackageCoverage(
        pkg: Package,
        cm: ClassManager,
        coverageBuilder: CoverageBuilder
    ): PackageCoverageInfo {
        val pc = PackageCoverageImpl(pkg.canonicalName, coverageBuilder.classes, coverageBuilder.sourceFiles)
        val packCov = getCommonCounters<PackageCoverageInfo>(pkg.canonicalName, pc)
        packCov.classes.addAll(getClassCoverage(cm, coverageBuilder))
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