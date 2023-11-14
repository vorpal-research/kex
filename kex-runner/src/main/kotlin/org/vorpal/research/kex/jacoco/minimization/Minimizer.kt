package org.vorpal.research.kex.jacoco.minimization

import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.container.Container
import java.io.File
import java.nio.file.Path

class TestCoverageInfo(
    val testName: Path,
    val satisfize: List<Int>
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
    fun execute() {
        val testCoverage = TestwiseCoverage(jar).execute(cm, analysisLevel)
        val excessTests = GreedyAlgorithm(testCoverage).minimized()

        for (classPath in excessTests) {
            val classFile = classPath.toFile()
            if (classFile.exists()) {
                classFile.delete()
            } else {
                org.vorpal.research.kthelper.logging.log.error("nonexistent class in Minimizer: $classFile")
            }
        }
    }
}