package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.trace.symbolic.ExecutionFailedResult
import org.vorpal.research.kex.trace.symbolic.protocol.Worker2MasterConnection
import org.vorpal.research.kthelper.logging.log

class ExecutorWorker(val ctx: ExecutionContext, val connection: Worker2MasterConnection) : Runnable {
    val executor: TestExecutor

    init {
        executor = TestExecutor(ctx)
        connection.connect()
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