package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kex.util.getJunit
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader


interface ExceptionReproductionChecker {
    fun isReproduced(testKlass: String): Boolean
}

class ExceptionReproductionCheckerImpl(
    val ctx: ExecutionContext,
    private val stackTrace: StackTrace
) : ExceptionReproductionChecker {
    override fun isReproduced(testKlass: String): Boolean {
        val resultingStackTrace = executeTest(testKlass) ?: return false
        return stackTrace `in` resultingStackTrace
    }

    private fun executeTest(testKlass: String): StackTrace? {
        val loader = CustomURLClassLoader(
            listOfNotNull(kexConfig.compiledCodeDirectory.toUri().toURL(), getJunit()?.path?.toUri()?.toURL()) +
                    ctx.classPath.map { it.toUri().toURL() }
        )
        val actualClass = loader.loadClass(testKlass)
        val instance = actualClass.getConstructor().newInstance()

        try {
            val setup = actualClass.getMethod("setup")
            setup.invoke(instance)
        } catch (e: Throwable) {
            log.error("Error during test setup execution:", e)
            return null
        }

        return try {
            val test = actualClass.getMethod("test")
            test.invoke(instance)
            unreachable("Provided test did not produce any crashes")
        } catch (e: Throwable) {
            var exception = e
            while (exception is InvocationTargetException) {
                exception = exception.targetException
            }
            exception.toStackTrace()
        }
    }

    private fun Throwable.toStackTrace(): StackTrace {
        val stringWriter = StringWriter()
        this.printStackTrace(PrintWriter(stringWriter))
        return StackTrace.parse(stringWriter.toString()).let {
            StackTrace(it.firstLine, it.stackTraceLines)
        }
    }

    class CustomURLClassLoader(
        urls: List<URL>
    ) : ClassLoader() {
        private val inner = URLClassLoader(urls.toTypedArray(), null)

        override fun loadClass(name: String?): Class<*> {
            return try {
                inner.loadClass(name)
            } catch (e: Throwable) {
                return parent.loadClass(name)
            }
        }
    }
}
