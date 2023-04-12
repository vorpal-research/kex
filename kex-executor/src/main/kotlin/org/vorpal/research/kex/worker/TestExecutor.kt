package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.trace.symbolic.TraceCollectorProxy
import org.vorpal.research.kex.trace.symbolic.protocol.ExceptionResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.SetupFailedResult
import org.vorpal.research.kex.trace.symbolic.protocol.SuccessResult
import org.vorpal.research.kex.trace.symbolic.protocol.TestExecutionRequest
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kthelper.logging.log
import java.net.URLClassLoader

class TestExecutor(
    val ctx: ExecutionContext
) {
    fun executeTest(request: TestExecutionRequest): ExecutionResult {
        val compiledClassLoader = URLClassLoader(
            arrayOf(
                kexConfig.outputDirectory.resolve(
                    kexConfig.getStringValue("compile", "compileDir", "compiled")
                ).toAbsolutePath().toUri().toURL()
            )
        )
        val javaClass = compiledClassLoader.loadClass(request.klass)
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
            log.error("Execution failed with an exception", e)
        }
        TraceCollectorProxy.disableCollector()
        log.debug("Collected state: ${collector.symbolicState}")
        return when {
            exception != null -> ExceptionResult(convertToDescriptor(exception), collector.symbolicState)
            else -> SuccessResult(collector.symbolicState)
        }
    }

}
