@file:Suppress("DuplicatedCode")

package org.vorpal.research.kex.jacoco.minimization

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
import org.vorpal.research.kex.jacoco.*
import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kex.launcher.ClassLevel
import org.vorpal.research.kex.launcher.MethodLevel
import org.vorpal.research.kex.launcher.PackageLevel
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.io.path.*
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TestwiseCoverage(
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
    ): TestwiseCoverageInfo {
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
                getTestwiseCoverage(classes, testClasses)
            }

            is ClassLevel -> {
                val klass = analysisLevel.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                getTestwiseCoverage(listOf(jacocoInstrumentedDir.resolve("$klass.class")), testClasses)
            }

            is MethodLevel -> {
                val method = analysisLevel.method
                val klass = method.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
                getTestwiseCoverage(listOf(jacocoInstrumentedDir.resolve("$klass.class")), testClasses)
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

    @Suppress("unused")
    class TestLogger : RunListener() {
        override fun testRunFinished(result: Result) {
            org.vorpal.research.kthelper.logging.log.info(buildString {
                appendLine("Result:")
                appendLine("run count ${result.runCount}")
                appendLine("ignore count ${result.ignoreCount}")
                appendLine("fail count ${result.failureCount}")
                appendLine("failures: ${result.failures.joinToString("\n")}")
            })
        }
    }

    private fun getTestwiseCoverage(
        classes: List<Path>,
        testClasses: List<Path>,
        logProgress: Boolean = true
    ): TestwiseCoverageInfo {
        var req: List<Int> = emptyList()
        val tests = mutableListOf<TestCoverageInfo>()

        for (testPath in testClasses) {
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

            if (logProgress) org.vorpal.research.kthelper.logging.log.debug("Running tests...")
            val classLoader = PathClassLoader(listOf(jacocoInstrumentedDir, compileDir))
            val testClassName = testPath.fullyQualifiedName(compileDir)
            val testClass = classLoader.loadClass(testClassName)
            if (logProgress) org.vorpal.research.kthelper.logging.log.debug("Running test $testClassName")
            val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")
            val jc = jcClass.newInstance()
//            if (kexConfig.getBooleanValue("testGen", "logJUnit", false)) {
//                jcClass.getMethod("addListener", classLoader.loadClass("org.junit.runner.notification.RunListener"))
//                    .invoke(jc, classLoader.loadClass("org.vorpal.research.kex."))
//
//                jc.addListener(TestLogger())
//            }
            val computerClass = classLoader.loadClass("org.junit.runner.Computer")
            jcClass.getMethod("run", computerClass, Class::class.java.asArray())
                .invoke(jc, computerClass.newInstance(), arrayOf(testClass))
//            jc.run(Computer(), testClass)

            if (logProgress) org.vorpal.research.kthelper.logging.log.debug("Analyzing Coverage...")
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

            req = getRequirements(coverageBuilder)
            val satisfied = getSatisfiedLines(coverageBuilder)
            tests.add(TestCoverageInfo(testClassName, satisfied))
        }
        return TestwiseCoverageInfo(req, tests)
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
            else -> unreachable { org.vorpal.research.kthelper.logging.log.error("Unknown common coverage info class ${T::class.java}") }
        } as T
    }

    private fun getRequirements(cb: CoverageBuilder): List<Int> {
        val res = mutableListOf<Int>()
        for (cc in cb.classes) {
            for (i in cc.firstLine..cc.lastLine) {
                if (getStatus(cc.getLine(i).status) != null) {
                    res.add(i)
                }
            }
        }
        return res
    }

    private fun getSatisfiedLines(cb: CoverageBuilder): List<Int> {
        val res = mutableListOf<Int>()
        for (cc in cb.classes) {
            for (i in cc.firstLine..cc.lastLine) {
                if (getStatus(cc.getLine(i).status) == true) {
                    res.add(i)
                }
            }
        }
        return res
    }

    private fun getStatus(status: Int): Boolean? {
        when (status) {
            ICounter.NOT_COVERED -> return false
            ICounter.PARTLY_COVERED -> return true
            ICounter.FULLY_COVERED -> return true
        }
        return null
    }

    class PathClassLoader(val paths: List<Path>) : ClassLoader() {
        private val cache = hashMapOf<String, Class<*>>()
        override fun loadClass(name: String): Class<*> {
            synchronized(this.getClassLoadingLock(name)) {
                if (name in cache) return cache[name]!!

                val fileName = name.replace(Package.CANONICAL_SEPARATOR, File.separatorChar) + ".class"
                for (path in paths) {
                    val resolved = path.resolve(fileName)
                    if (resolved.exists()) {
                        val bytes = resolved.readBytes()
                        val klass = defineClass(name, bytes, 0, bytes.size)
                        cache[name] = klass
                        return klass
                    }
                }
            }
            return parent?.loadClass(name) ?: throw ClassNotFoundException()
        }
    }
}