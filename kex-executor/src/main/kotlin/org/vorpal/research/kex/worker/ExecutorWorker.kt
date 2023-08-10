package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionFailedResult
import org.vorpal.research.kex.trace.symbolic.protocol.Worker2MasterConnection
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log
import kotlin.time.Duration.Companion.seconds

class ExecutorWorker(
    val ctx: ExecutionContext,
    private val connection: Worker2MasterConnection
) : Runnable {
    val executor: TestExecutor = TestExecutor(ctx)
    private val timeout = kexConfig.getIntValue("runner", "timeout", 100).seconds

    init {
        ktassert(connection.connect(timeout))
    }

    override fun run() {
        while (true) {
            val request = connection.receive()
            val result = try {
                executor.executeTest(request)
            } catch (e: Throwable) {
                log.error("Failed: $e")
                ExecutionFailedResult(e.message ?: "")
            }
            connection.send(result)
        }
    }
}
