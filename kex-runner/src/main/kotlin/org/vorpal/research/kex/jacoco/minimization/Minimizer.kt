package org.vorpal.research.kex.jacoco.minimization

import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.container.Container
import java.io.File

class TestCoverageInfo(
    val testName: String,
    val satisfize: List<Int>
) {
}

class TestwiseCoverageInfo(
    val req: List<Int>,
    val tests: List<TestCoverageInfo>
)

public class Minimizer(
    val jar: List<Container>,
    val cm: ClassManager,
    val analysisLevel: AnalysisLevel
) {
    fun execute() {
        val filePath = "minimization-results.txt"
        val file = File(filePath)
        if (!file.exists()) {
            file.createNewFile()
        }
        val testCoverage = TestwiseCoverage(jar).execute(cm, analysisLevel)
        val mimimized = GreedyAlgorithm(testCoverage, file).minimized()
    }
}