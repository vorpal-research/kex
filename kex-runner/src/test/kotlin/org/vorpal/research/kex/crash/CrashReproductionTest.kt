package org.vorpal.research.kex.crash

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.KexRunnerTest
import org.vorpal.research.kex.asm.analysis.crash.CrashReproductionChecker
import org.vorpal.research.kex.asm.analysis.crash.StackTrace
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.compiledCodeDirectory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
abstract class CrashReproductionTest(testDirectoryName: String) : KexRunnerTest(testDirectoryName) {
    companion object {
        private const val DEPTH = 3
        private const val SETUP_METHOD = "setup"
        private const val TEST_METHOD = "test"
    }

    override fun createTraceCollector(context: ExecutionContext) = object : MethodVisitor {
        override val cm: ClassManager
            get() = context.cm

        override fun cleanup() {}
    }

    fun assertCrash(expectedStackTrace: StackTrace) = withConfigOption("testGen", "surroundInTryCatch", "false") {
        executePipeline(analysisContext.cm, Package.defaultPackage) {
            +ClassInstantiationDetector(analysisContext)
        }

        log.error(expectedStackTrace)
        val crashes = CrashReproductionChecker.run(analysisContext, expectedStackTrace)
        assertTrue(crashes.isNotEmpty())
        for (crash in crashes) {
            val resultingStackTrace = executeTest(crash)
            assertEquals(expectedStackTrace.throwable, resultingStackTrace.throwable)
            assertEquals(expectedStackTrace.stackTraceLines, resultingStackTrace.stackTraceLines)
        }
    }

    private fun executeTest(testKlass: String): StackTrace {
        val loader = URLClassLoader(arrayOf(kexConfig.compiledCodeDirectory.toUri().toURL()))
        val actualClass = loader.loadClass(testKlass)
        val instance = actualClass.getConstructor().newInstance()

        try {
            val setup = actualClass.getMethod(SETUP_METHOD)
            setup.invoke(instance)
        } catch (e: Throwable) {
            throw e
        }

        return try {
            val test = actualClass.getMethod(TEST_METHOD)
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

    inline fun produceStackTrace(body: () -> Unit): StackTrace = try {
        body()
        unreachable("Body did not produce any exception")
    } catch (e: Throwable) {
        e.toStackTrace()
    }

    fun Throwable.toStackTrace(): StackTrace {
        val stringWriter = StringWriter()
        this.printStackTrace(PrintWriter(stringWriter))
        return StackTrace.parse(stringWriter.toString()).let {
            StackTrace(it.firstLine, it.stackTraceLines.take(DEPTH))
        }
    }

    private val StackTrace.throwable get() = firstLine.takeWhile { it != ':' }
}
