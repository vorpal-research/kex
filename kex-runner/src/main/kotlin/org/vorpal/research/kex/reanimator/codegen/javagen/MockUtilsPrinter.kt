package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.testcaseDirectory
import java.nio.file.Path

class MockUtilsPrinter(
    val packageName: String
) {
    private val builder = JavaBuilder(packageName)
    val klass = builder.run { klass(packageName, MOCK_UTILS_CLASS) }
    val initMockito: JavaBuilder.JavaFunction
//    val innerClass = builder.run { klass(packageName, MOCK_INIT_TEST_CLASS) }

//    val newPrimitiveArrayMap = mutableMapOf<String, JavaBuilder.JavaFunction>()

    companion object {
        const val MOCK_UTILS_CLASS = "MockUtils"

        const val MOCK_INIT_TEST_CLASS = "InitTest"
        private val mockUtilsInstances = mutableMapOf<Pair<Path, String>, MockUtilsPrinter>()
        fun mockUtils(packageName: String): MockUtilsPrinter {
            val testDirectory = kexConfig.testcaseDirectory
            return mockUtilsInstances.getOrPut(testDirectory to packageName) {
                val utils = MockUtilsPrinter(packageName)
                val targetFileName = "MockUtils.java"
                val targetFile = testDirectory
                    .resolve(packageName.asmString)
                    .resolve(targetFileName)
                    .toAbsolutePath()
                    .toFile().also { it.parentFile?.mkdirs() }
                targetFile.writeText(utils.builder.toString())
                utils
            }
        }

        @Suppress("unused")
        fun invalidateAll() {
            mockUtilsInstances.clear()
        }
    }

    init {
        with(builder) {
            import("org.mockito.Mockito")
            import ("org.junit.Test")

            with(klass) {
                initMockito = method("mockitoInitTest") {
                    +"Object mock = Mockito.mock(Object.class)"
                    +"assert (mock.hashCode() == 0 || mock.hashCode() != 0)"
                }.apply {
                    returnType = void
                    annotations += "Test"
                }
                staticClass(MOCK_INIT_TEST_CLASS) {
                    method("mockitoInitTest") {
                        +"Object mock = Mockito.mock(Object.class)"
                        +"assert (mock.hashCode() == 0 || mock.hashCode() != 0)"
                    }.apply {
                        returnType = void;
                        annotations += "Test"
                    }
                }
            }
        }
    }
}
