package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.testcaseDirectory
import java.nio.file.Path

class MockUtilsPrinter(
    val packageName: String
) {
    private val builder = JavaBuilder(packageName)
    val klass = builder.run { klass(packageName, MOCK_UTILS_CLASS) }
    val setupMethod: JavaBuilder.JavaFunction


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
            import ("org.mockito.invocation.InvocationOnMock")
            import ("org.mockito.stubbing.Answer")

            import ("org.junit.Test")
            import ("org.junit.Rule")
            import ("org.junit.rules.Timeout")

            import("java.lang.Class")
            import("java.lang.reflect.Method")
            import("java.lang.reflect.Constructor")
            import("java.lang.reflect.Field")
            import("java.lang.reflect.Array")
            import("java.lang.reflect.Modifier")
            import("sun.misc.Unsafe")

            import ("java.lang.Throwable")
            import ("java.lang.IllegalStateException")
            import ("java.util.concurrent.TimeUnit")
            import ("org.junit.Before")


            with(klass) {
                setupMethod = method("setupMethod") {
                    arguments += arg("name", type("String"))
                    arguments += arg("argTypes", type("Class<?>[]"))
                    arguments += arg("instance", type("Object"))
                    arguments += arg("anys", type("Object[]"))
                    arguments += arg("returns", type("Object[]"))

                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"
                    returnType = type("void")

                    +"Class<?> klass = instance.getClass()"
                    +"Method method = klass.getDeclaredMethod(name, argTypes)"
                    +"method.setAccessible(true)"
                    +"""Mockito.when(method.invoke(instance, anys)).thenAnswer(new Answer<Object>() {
                    int cur = 0;
                    @Override
                    public Object answer(InvocationOnMock invocation) {
                        if (cur == returns.length - 1){
                            return returns[cur];
                        }
                        return returns[cur++];
                    }
                })"""
                }

                staticClass(MOCK_INIT_TEST_CLASS) {
                    field("globalTimeout", type("Timeout")) {
                        visibility = Visibility.PUBLIC
                        initializer = "new Timeout($testTimeout, TimeUnit.SECONDS)"
                        annotations += "Rule"
                    }
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
