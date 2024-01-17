package org.vorpal.research.kex.jacoco.minimization

import java.nio.file.Path

interface TestSuiteMinimizer {
    /**
     * Minimizes the test suite based on the given coverage information.
     *
     * @param testCoverage The test coverage information.
     * @param deleteMinimized Whether to delete the minimized files. Defaults to true.
     * @return A set of paths to the remaining tests.
     */
    fun minimize(testCoverage: TestwiseCoverageInfo, deleteMinimized: Boolean = true): Set<Path>
}
