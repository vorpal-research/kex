package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.trace.symbolic.TraceCollectorProxy
import org.vorpal.research.kex.trace.symbolic.protocol.*
import org.vorpal.research.kex.util.asArray
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log

class TestExecutor(
    val ctx: ExecutionContext
) {
    private var isFirstRun: Boolean = true

    fun executeTest(request: TestExecutionRequest): ExecutionResult {
        val javaClass = ctx.loader.loadClass(request.klass)
        if (isFirstRun) {
            log.debug { "First run with JUnit" }
            executeTestFromJUnit(javaClass)
            isFirstRun = false
        } else {
            log.debug { "No JUnit run" }
        }

        val instance = javaClass.getConstructor().newInstance()
        log.debug("Loaded a test class and created an instance")

        when (val setupMethod = request.setupMethod) {
            null -> {}
            else -> try {
                val setup = javaClass.getMethod(setupMethod)
                setup.invoke(instance)
            } catch (e: Throwable) {
                log.error("Setup failed with an exception", e)
                return SetupFailedResult(e.message ?: "")
            }
        }
        log.debug("Executed setup")

        val collector = TraceCollectorProxy.enableCollector(ctx, NameMapperContext())
        var exception: Throwable? = null
        try {
            val test = javaClass.getMethod(request.testMethod)
            test.invoke(instance)
        } catch (e: Throwable) {
            exception = e
            log.error("Execution failed with an exception $e") // no stacktrace???
        }
        TraceCollectorProxy.disableCollector()
        log.debug("Collected state: {}", collector.symbolicState)
        return when {
            exception != null -> ExceptionResult(convertToDescriptor(exception), collector.symbolicState)
            else -> SuccessResult(collector.symbolicState)
        }
    }

    private fun executeTestFromJUnit(testClass: Class<*>?): Unit = try {
        val jcClass = ctx.loader.loadClass("org.junit.runner.JUnitCore")
        val jc = jcClass.getConstructor().newInstance()
        log.debug { "Created JUnitCoreInstance" }
        jcClass.getMethod("run", Class::class.java.asArray())
            .invoke(jc, arrayOf(testClass))
        log.debug { "JUnit successfully executed" }
    } catch (e: Throwable) {
        log.error("JUnit execution failed with an exception", e)
    }
}
