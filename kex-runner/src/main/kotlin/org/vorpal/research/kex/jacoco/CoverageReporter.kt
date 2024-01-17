@file:Suppress("MemberVisibilityCanBePrivate")

package org.vorpal.research.kex.jacoco

import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.ICoverageNode
import org.jacoco.core.data.ExecutionData
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
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        val testClasses = Files.walk(compileDir).filter { it.isClass }.toList()
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
                    .toList()
                val coverageBuilder = getCoverageBuilderAndCleanup(classes, testClasses)
                getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
            }

            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                val coverageBuilder = getCoverageBuilderAndCleanup(
                    listOf(jacocoInstrumentedDir.resolve("$klass.class")),
                    testClasses
                )
                getClassCoverage(cm, coverageBuilder).first()
            }

            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                val coverageBuilder = getCoverageBuilderAndCleanup(
                    listOf(jacocoInstrumentedDir.resolve("$klass.class")),
                    testClasses
                )
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

    fun computeCoverageSaturation(
        cm: ClassManager,
        analysisLevel: AnalysisLevel,
    ): SortedMap<Duration, CommonCoverageInfo> {
        val testClasses = Files.walk(compileDir).filter { it.isClass }.toList()
        val batchedTestClasses = testClasses.batchByTime()
        val maxTime = batchedTestClasses.lastKey()

        val coverageContext = when (analysisLevel) {
            is PackageLevel -> buildCoverageContext(
                Files.walk(jacocoInstrumentedDir)
                    .filter { it.isClass }
                    .filter { analysisLevel.pkg.isParent(it.fullyQualifiedName(jacocoInstrumentedDir).asmString) }
                    .toList()
            )

            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                buildCoverageContext(listOf(jacocoInstrumentedDir.resolve("$klass.class")))
            }

            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                buildCoverageContext(listOf(jacocoInstrumentedDir.resolve("$klass.class")))
            }
        }
        val executionData = computeExecutionData(coverageContext, testClasses)

        val coverageSaturation = batchedTestClasses.mapValuesTo(sortedMapOf()) { (duration, batchedTests) ->
            log.debug("Running tests for batch {} / {}", duration.inWholeSeconds, maxTime.inWholeSeconds)
            val coverageBuilder = buildCoverageBuilder(coverageContext, batchedTests, executionData)
            when (analysisLevel) {
                is PackageLevel -> getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
                is ClassLevel -> getClassCoverage(cm, coverageBuilder).first()
                is MethodLevel -> getMethodCoverage(coverageBuilder, analysisLevel.method)!!
            }
        }

        resetCoverageContext(coverageContext)
        return coverageSaturation
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

    private fun getCoverageBuilderAndCleanup(
        classes: List<Path>,
        testClasses: List<Path>
    ): CoverageBuilder {
        val context = buildCoverageContext(classes)
        val executionData = computeExecutionData(context, testClasses)
        val coverageBuilder = buildCoverageBuilder(context, testClasses, executionData)
        resetCoverageContext(context)
        return coverageBuilder
    }

    class CoverageContext(
        val runtime: LoggerRuntime,
        val classes: List<Path>,
        val originalClassBytecode: Map<Path, ByteArray>
    )

    private fun buildCoverageContext(
        classes: List<Path>
    ): CoverageContext {
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
        return CoverageContext(runtime, classes, originalClasses)
    }

    private fun buildCoverageBuilder(
        context: CoverageContext,
        testClasses: List<Path>,
        executionData: Map<Path, ExecutionDataStore>,
        logProgress: Boolean = true
    ): CoverageBuilder {
        val mergedExecutionData = ExecutionDataStore()
        for (testPath in testClasses) {
            val executions = executionData[testPath] ?: continue
            for (data in executions.contents) {
                mergedExecutionData.put(data)
            }
        }

        if (logProgress) log.debug("Analyzing Coverage...")
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(mergedExecutionData, coverageBuilder)
        for (className in context.classes) {
            context.originalClassBytecode[className]?.inputStream()?.use {
                tryOrNull {
                    analyzer.analyzeClass(it, className.fullyQualifiedName(jacocoInstrumentedDir))
                }
            }
        }
        return coverageBuilder
    }

    private fun computeExecutionData(
        context: CoverageContext,
        testClasses: List<Path>,
        logProgress: Boolean = true
    ): Map<Path, ExecutionDataStore> {
        val datum = mutableMapOf<Path, ExecutionDataStore>()
        val data = RuntimeData()
        context.runtime.startup(data)

        if (logProgress) log.debug("Running tests...")
        val classLoader = PathClassLoader(listOf(jacocoInstrumentedDir, compileDir))
        for (testPath in testClasses) {
            val testClassName = testPath.fullyQualifiedName(compileDir)
            val testClass = classLoader.loadClass(testClassName)
            if (logProgress) log.debug("Running test $testClassName")

            val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")
            val jc = jcClass.newInstance()
            val computerClass = classLoader.loadClass("org.junit.runner.Computer")
            jcClass.getMethod("run", computerClass, Class::class.java.asArray())
                .invoke(jc, computerClass.newInstance(), arrayOf(testClass))

            val executionData = ExecutionDataStore()
            data.collect(executionData, SessionInfoStore(), false)
            datum[testPath] = executionData.deepCopy()
            data.reset()
        }

        context.runtime.shutdown()
        return datum
    }

    private fun ExecutionDataStore.deepCopy(): ExecutionDataStore {
        val executionDataCopy = ExecutionDataStore()
        for (content in contents) {
            val ed = ExecutionData(content.id, content.name, content.probes.copyOf())
            executionDataCopy.put(ed)
        }
        return executionDataCopy
    }

    private fun resetCoverageContext(context: CoverageContext) {
        for (className in context.classes) {
            val bytecode = context.originalClassBytecode[className] ?: continue
            className.writeBytes(bytecode)
        }
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
}

fun reportCoverage(
    containers: List<Container>,
    cm: ClassManager,
    analysisLevel: AnalysisLevel,
    mode: String
) {
    if (kexConfig.getBooleanValue("kex", "computeCoverage", true)) {
        val coverageInfo = when {
            kexConfig.getBooleanValue("kex", "computeCoverageSaturation", true) -> {
                val coverageSaturation = CoverageReporter(containers)
                    .computeCoverageSaturation(cm, analysisLevel)
                PermanentSaturationCoverageInfo.putNewInfo(
                    "concolic",
                    analysisLevel.toString(),
                    coverageSaturation.toList()
                )
                PermanentSaturationCoverageInfo.emit()
                coverageSaturation[coverageSaturation.lastKey()]!!
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
