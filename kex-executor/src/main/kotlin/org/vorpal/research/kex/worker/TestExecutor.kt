package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.trace.symbolic.*
import org.vorpal.research.kex.trace.symbolic.protocol.TestExecutionRequest
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log

class TestExecutor(
    val ctx: ExecutionContext
) {
    fun executeTest(request: TestExecutionRequest): ExecutionResult {
        val javaClass = Class.forName(request.klass)
        val instance = javaClass.getConstructor().newInstance()

        when (val setupMethod = request.setupMethod) {
            null -> {}
            else -> try {
                val setup = javaClass.getMethod(setupMethod)
                setup.invoke(instance)
            } catch (e: Throwable) {
                log.error(e)
                e.printStackTrace(System.err)
                return SetupFailedResult(e.message ?: "", symbolicState())
            }
        }

        val collector = TraceCollectorProxy.enableCollector(ctx, NameMapperContext())
        var exception: Throwable? = null
        try {
            val test = javaClass.getMethod(request.testMethod)
            test.invoke(instance)
        } catch (e: Throwable) {
            exception = e
        }
        TraceCollectorProxy.disableCollector()
        log.debug("Collected state: ${collector.symbolicState}")
        return when {
            exception != null -> ExceptionResult(convertToDescriptor(exception), collector.symbolicState)
            else -> SuccessResult(collector.symbolicState)
        }
    }

}