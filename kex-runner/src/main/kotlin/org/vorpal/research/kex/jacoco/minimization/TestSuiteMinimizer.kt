package org.vorpal.research.kex.jacoco.minimization

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.reanimator.codegen.javagen.ReflectionUtilsPrinter
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.testcaseDirectory
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

interface TestSuiteMinimizer {
    /**
     * Minimizes the test suite based on the given coverage information.
     *
     * @param testCoverage The test coverage information.
     * @param deleteMinimized Whether to delete the minimized files. Defaults to true.
     * @return A set of paths to the remaining tests.
     */
    fun minimize(testCoverage: TestwiseCoverageInfo, deleteMinimized: Boolean = true): Set<Path>

    companion object {
        // there will be more
        private fun protectedFiles(): Set<Path> = ReflectionUtilsPrinter.reflectionUtilsClasses()
        fun deleteTestCases(compiledTests: Set<Path>) {
            val protectedFiles = protectedFiles()
            for (compiledTestPath in compiledTests) {
                val relativePath = kexConfig.compiledCodeDirectory.relativize(compiledTestPath)
                val testClassPath = relativePath.toString().replace(".class", ".java")
                val testCasePath = kexConfig.testcaseDirectory.resolve(testClassPath)
                if (testCasePath in protectedFiles) continue

                if (!testCasePath.deleteIfExists()) {
                    log.error("Failed to delete file ${testCasePath.toAbsolutePath()}")
                }
                if (!compiledTestPath.deleteIfExists()) {
                    log.error("Failed to delete file ${compiledTestPath.toAbsolutePath()}")
                }
            }
        }
    }
}
