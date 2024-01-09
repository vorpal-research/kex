package org.vorpal.research.kex.jacoco.minimization

import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path

class TestCoverageInfo(
    val testName: Path,
    val satisfies: List<Int>
) {
}

class TestwiseCoverageInfo(
    val req: List<Int>,
    val tests: List<TestCoverageInfo>
)

class Minimizer(
    private val jar: List<Container>,
    val cm: ClassManager,
    private val analysisLevel: AnalysisLevel
) {
    fun execute(report: Boolean = false, mode: String = "symbolic") {
        val testWiseCoverageReporter = TestwiseCoverageReporter(jar)
        val testCoverage = testWiseCoverageReporter.execute(analysisLevel)
        val excessTests = GreedyTestReductionImpl(testCoverage).minimized()

        for (classPath in excessTests) {
            val classFile = classPath.toFile()
            if (classFile.exists()) {
                classFile.delete()
            } else {
                log.error("nonexistent class in Minimizer: $classFile")
            }
        }

        if (report) {
            testWiseCoverageReporter.reportCoverage(cm, analysisLevel, mode)
        }
    }
}
