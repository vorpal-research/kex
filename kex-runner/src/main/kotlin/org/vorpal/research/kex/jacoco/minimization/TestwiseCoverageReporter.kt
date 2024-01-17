@file:Suppress("DuplicatedCode")

package org.vorpal.research.kex.jacoco.minimization

import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.vorpal.research.kex.jacoco.CoverageReporter
import org.vorpal.research.kex.launcher.AnalysisLevel
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path

class TestCoverageInfo(
    val testName: Path,
    val satisfies: Set<Pair<String, Int>>
)

class TestwiseCoverageInfo(
    val req: Set<Pair<String, Int>>,
    val tests: List<TestCoverageInfo>
)


class TestwiseCoverageReporter(
    classManager: ClassManager,
    containers: List<Container> = listOf()
) : CoverageReporter(classManager, containers) {

    fun computeTestwiseCoverageInfo(
        analysisLevel: AnalysisLevel,
        testClasses: Set<Path> = this.allTestClasses
    ): TestwiseCoverageInfo {
        ktassert(this.allTestClasses.containsAll(testClasses)) {
            log.error("Unexpected set of test classes")
        }

        initializeContext(scanTargetClasses(analysisLevel))

        val req = mutableSetOf<Pair<String, Int>>()
        val tests = mutableListOf<TestCoverageInfo>()
        val coverageBuilder = buildCoverageBuilder(coverageContext, testClasses, executionData)
        for (testPath in testClasses) {
            req += getRequirements(coverageBuilder)
            val satisfied = getSatisfiedLines(coverageBuilder)
            tests += TestCoverageInfo(testPath, satisfied)
        }
        return TestwiseCoverageInfo(req, tests)
    }

    private fun getRequirements(cb: CoverageBuilder): Set<Pair<String, Int>> = buildSet {
        for (cc in cb.classes) {
            for (i in cc.firstLine..cc.lastLine) {
                if (getStatus(cc.getLine(i).status) != null) {
                    add(cc.name to i)
                }
            }
        }
    }

    private fun getSatisfiedLines(cb: CoverageBuilder): Set<Pair<String, Int>> = buildSet {
        for (cc in cb.classes) {
            for (i in cc.firstLine..cc.lastLine) {
                if (getStatus(cc.getLine(i).status) == true) {
                    add(cc.name to i)
                }
            }
        }
    }

    private fun getStatus(status: Int): Boolean? {
        when (status) {
            ICounter.NOT_COVERED -> return false
            ICounter.PARTLY_COVERED -> return true
            ICounter.FULLY_COVERED -> return true
        }
        return null
    }
}
