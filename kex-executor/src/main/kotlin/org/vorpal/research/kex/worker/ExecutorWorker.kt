package org.vorpal.research.kex.worker

import kotlinx.coroutines.runBlocking
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionFailedResult
import org.vorpal.research.kex.trace.symbolic.protocol.Worker2MasterConnection
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log

class ExecutorWorker(
    val ctx: ExecutionContext,
    private val connection: Worker2MasterConnection
) : Runnable, AutoCloseable {
    val executor: TestExecutor = TestExecutor(ctx)

    override fun run() = runBlocking(newFixedThreadPoolContextWithMDC(1, "worker")) {
        ktassert(connection.connect())
        while (true) {
            val request = connection.receive() ?: return@runBlocking
            val result = try {
                executor.executeTest(request)
            } catch (e: Throwable) {
                log.error("Failed:", e)
                ExecutionFailedResult(e.message ?: "")
            }
            if (!connection.send(result)) {
                return@runBlocking
            }
        }
    }

    override fun close() {
        connection.close()
    }
}
