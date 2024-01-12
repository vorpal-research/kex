@file:Suppress("MemberVisibilityCanBePrivate")

package org.vorpal.research.kex.jacoco

import com.jetbrains.rd.util.string.printToString
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
import org.junit.runner.Result
import org.junit.runner.notification.RunListener
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kex.launcher.ClassLevel
import org.vorpal.research.kex.launcher.MethodLevel
import org.vorpal.research.kex.launcher.PackageLevel
import org.vorpal.research.kex.util.PathClassLoader
import org.vorpal.research.kex.util.PermanentCoverageInfo
import org.vorpal.research.kex.util.PermanentSaturationCoverageInfo
import org.vorpal.research.kex.util.asArray
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.deleteOnExit
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    override val ratio: Double get() = when (total) {
        0 -> 0.0
        else -> covered.toDouble() / total
    }
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

        return name == other.name
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
                appendLine(it.print(true))
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
                appendLine(it.print(true))
            }
        }
        appendLine(this@PackageCoverageInfo.toString())
    }
}

class CoverageReporter(
    containers: List<Container> = listOf()
) {
    private val jacocoInstrumentedDir = kexConfig.outputDirectory.resolve(
        kexConfig.getPathValue("testGen", "jacocoDir", "jacoco/")
    ).also {
        it.toFile().mkdirs()
    }.also {
        deleteOnExit(it)
    }
    private val compileDir = kexConfig.compiledCodeDirectory

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
        val testClasses = Files.walk(compileDir).filter { it.isClass }.collect(Collectors.toList())
        val result = when (analysisLevel) {
            is PackageLevel -> {
                val classes = Files.walk(jacocoInstrumentedDir)
                    .filter { it.isClass }
                    .filter {
                        analysisLevel.pkg.isParent(
                            it.fullyQualifiedName(jacocoInstrumentedDir)
                                .asmString
                        )
                    }
                    .collect(Collectors.toList())
                val coverageBuilder = getCoverageBuilder(classes, testClasses)
                getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
            }

            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                val coverageBuilder =
                    getCoverageBuilder(listOf(jacocoInstrumentedDir.resolve("$klass.class")), testClasses)
                getClassCoverage(cm, coverageBuilder).first()
            }

            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                val coverageBuilder =
                    getCoverageBuilder(listOf(jacocoInstrumentedDir.resolve("$klass.class")), testClasses)
                getMethodCoverage(coverageBuilder, method)!!
            }
        }
        return result
    }

    fun List<Path>.batchByTime(step: Duration = 1.seconds): SortedMap<Duration, List<Path>> {
        if (this.isEmpty()) return sortedMapOf()
        val ordered = this.map {
            val attr = Files.readAttributes(it, BasicFileAttributes::class.java)
            it to attr.creationTime().toMillis().milliseconds
        }.sortedBy { it.second }
        val startTime = ordered.first().second
        val endTime = ordered.last().second + 1.seconds
        val batches = mutableMapOf(
            0.seconds to emptyList<Path>()
        )
        var nextTime = startTime + step
        for ((index, file) in ordered.withIndex()) {
            if (file.second > nextTime) {
                batches[nextTime - startTime] = ordered.subList(0, index).map { it.first }
                nextTime += step
            }
        }
        while (nextTime <= endTime) {
            batches[nextTime - startTime] = ordered.map { it.first }
            nextTime += step
        }
        return batches.toSortedMap()
    }

    fun computeSaturationCoverage(
        cm: ClassManager,
        analysisLevel: AnalysisLevel,
    ): SortedMap<Duration, CommonCoverageInfo> {
        val testClasses = Files.walk(compileDir).filter { it.isClass }.collect(Collectors.toList())
        val batchedTestClasses = testClasses.batchByTime()
        val maxTime = batchedTestClasses.lastKey()
        return when (analysisLevel) {
            is PackageLevel -> {
                val classes = Files.walk(jacocoInstrumentedDir)
                    .filter { it.isClass }
                    .filter {
                        analysisLevel.pkg.isParent(
                            it.fullyQualifiedName(jacocoInstrumentedDir)
                                .asmString
                        )
                    }
                    .collect(Collectors.toList())
                batchedTestClasses.mapValues { (duration, batchedTests) ->
                    log.debug("Running tests for batch {} / {}", duration.inWholeSeconds, maxTime.inWholeSeconds)
                    val coverageBuilder = getCoverageBuilder(classes, batchedTests, logProgress = false)
                    getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
                }
            }

            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                batchedTestClasses.mapValues { (duration, batchedTests) ->
                    log.debug("Running tests for batch {} / {}", duration.inWholeSeconds, maxTime.inWholeSeconds)
                    val coverageBuilder = getCoverageBuilder(
                        listOf(jacocoInstrumentedDir.resolve("$klass.class")),
                        batchedTests,
                        logProgress = false
                    )
                    getClassCoverage(cm, coverageBuilder).first()
                }
            }

            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                batchedTestClasses.mapValues { (duration, batchedTests) ->
                    log.debug("Running tests for batch {} / {}", duration.inWholeSeconds, maxTime.inWholeSeconds)
                    val coverageBuilder = getCoverageBuilder(
                        listOf(jacocoInstrumentedDir.resolve("$klass.class")),
                        batchedTests,
                        logProgress = false
                    )
                    getMethodCoverage(coverageBuilder, method)!!
                }
            }
        }.toSortedMap()
    }

    @Suppress("unused")
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

    private fun getCoverageBuilder(
        classes: List<Path>,
        testClasses: List<Path>,
        logProgress: Boolean = true
    ): CoverageBuilder {
        val runtime = LoggerRuntime()
        val originalClasses = mutableMapOf<Path, ByteArray>()
        for (classPath in classes) {
            originalClasses[classPath] = classPath.readBytes()
            val instrumented = classPath.inputStream().use {
                val fullyQualifiedName = classPath.fullyQualifiedName(jacocoInstrumentedDir)
                val instr = Instrumenter(runtime)
                instr.instrument(it, fullyQualifiedName)
            }
            classPath.writeBytes(instrumented)
        }
        val data = RuntimeData()
        runtime.startup(data)

        if (logProgress) log.debug("Running tests...")
        val classLoader = PathClassLoader(listOf(jacocoInstrumentedDir, compileDir))
        for (testPath in testClasses) {
            val testClassName = testPath.fullyQualifiedName(compileDir)
            val testClass = classLoader.loadClass(testClassName)
            if (logProgress) log.debug("Running test $testClassName")
            val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")
            val jc = jcClass.newInstance()
            // TODO: addListener via reflection
//            if (kexConfig.getBooleanValue("testGen", "logJUnit", false)) {
//                jcClass.getMethod("addListener", classLoader.loadClass("org.junit.runner.notification.RunListener"))
//                    .invoke(jc, classLoader.loadClass("org.vorpal.research.kex."))
//
//                jc.addListener(TestLogger())
//            }
            val computerClass = classLoader.loadClass("org.junit.runner.Computer")
            val returnValue = jcClass.getMethod("run", computerClass, Class::class.java.asArray())
                .invoke(jc, computerClass.newInstance(), arrayOf(testClass))
            log.debug("Failures:")
            (returnValue as? Result)?.failures?.forEach {
                log.debug(it.trace)
            }
//            jc.run(Computer(), testClass)
        }

        if (logProgress) log.debug("Analyzing Coverage...")
        val executionData = ExecutionDataStore()
        val sessionInfos = SessionInfoStore()
        data.collect(executionData, sessionInfos, false)
        runtime.shutdown()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(executionData, coverageBuilder)
        for (className in classes) {
            originalClasses[className]?.inputStream()?.use {
                tryOrNull {
                    analyzer.analyzeClass(it, className.fullyQualifiedName(jacocoInstrumentedDir))
                }
            }
            className.writeBytes(originalClasses[className]!!)
        }
        return coverageBuilder
    }

    private val String.fullyQualifiedName: String
        get() = removeSuffix(".class").javaString

    private fun Path.fullyQualifiedName(base: Path): String =
        relativeTo(base).toString()
            .removePrefix("..")
            .removePrefix(File.separatorChar.toString())
            .replace(File.separatorChar, Package.CANONICAL_SEPARATOR)
            .removeSuffix(".class")

    private fun getClassCoverage(
        cm: ClassManager,
        coverageBuilder: CoverageBuilder
    ): Set<ClassCoverageInfo> =
        coverageBuilder.classes.mapTo(mutableSetOf()) {
            val kfgClass = cm[it.name]
            val classCov = getCommonCounters<ClassCoverageInfo>(it.name, it)
            for (mc in it.methods) {
                val kfgMethod = kfgClass.getMethod(mc.name, mc.desc)
                classCov.methods += getCommonCounters<MethodCoverageInfo>(kfgMethod.prototype.fullyQualifiedName, mc)
            }
            classCov
        }

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

//    class PathClassLoader(val paths: List<Path>) : ClassLoader() {
//        private val cache = hashMapOf<String, Class<*>>()
//        override fun loadClass(name: String): Class<*> {
//            synchronized(this.getClassLoadingLock(name)) {
//                if (name in cache) return cache[name]!!
//
//                val fileName = name.replace(Package.CANONICAL_SEPARATOR, File.separatorChar) + ".class"
//                for (path in paths) {
//                    val resolved = path.resolve(fileName)
//                    if (resolved.exists()) {
//                        val bytes = resolved.readBytes()
//                        val klass = defineClass(name, bytes, 0, bytes.size)
//                        cache[name] = klass
//                        return klass
//                    }
//                }
//            }
//            return parent?.loadClass(name) ?: throw ClassNotFoundException()
//        }
//    }
}

fun reportCoverage(
    containers: List<Container>,
    cm: ClassManager,
    analysisLevel: AnalysisLevel,
    mode: String
) {
    if (kexConfig.getBooleanValue("kex", "computeCoverage", true)) {
        val coverageInfo = when {
            kexConfig.getBooleanValue("kex", "computeSaturationCoverage", true) -> {
                val saturationCoverage = CoverageReporter(containers)
                    .computeSaturationCoverage(cm, analysisLevel)
                PermanentSaturationCoverageInfo.putNewInfo(
                    "concolic",
                    analysisLevel.toString(),
                    saturationCoverage.toList()
                )
                PermanentSaturationCoverageInfo.emit()
                saturationCoverage[saturationCoverage.lastKey()]!!
            }

            else -> CoverageReporter(containers).execute(cm, analysisLevel)
        }

        log.info(
            coverageInfo.print(kexConfig.getBooleanValue("kex", "printDetailedCoverage", false))
        )

        PermanentCoverageInfo.putNewInfo(mode, analysisLevel.toString(), coverageInfo)
        PermanentCoverageInfo.emit()
    }
}
