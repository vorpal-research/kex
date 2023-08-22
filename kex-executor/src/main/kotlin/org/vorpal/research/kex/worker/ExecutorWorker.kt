package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionFailedResult
import org.vorpal.research.kex.trace.symbolic.protocol.Worker2MasterConnection
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log

class ExecutorWorker(
    val ctx: ExecutionContext,
    private val connection: Worker2MasterConnection
) : Runnable, AutoCloseable {
    val executor: TestExecutor = TestExecutor(ctx)

    init {
        ktassert(connection.connect())
    }

    override fun run() {
        while (true) {
            val request = connection.receive() ?: return
            val result = try {
                executor.executeTest(request)
            } catch (e: Throwable) {
                log.error("Failed: $e")
                ExecutionFailedResult(e.message ?: "")
            }
            if (!connection.send(result)) {
                return
            }
        }
    }

    override fun close() {
        connection.close()
    }
}
