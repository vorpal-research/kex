package org.vorpal.research.kex.worker

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.trace.symbolic.protocol.Worker2MasterConnection

class ExecutorWorker(val ctx: ExecutionContext, val connection: Worker2MasterConnection) : Runnable {
    val executor: TestExecutor

    init {
        executor = TestExecutor(ctx)
        connection.connect()
    }

    override fun run() {
        while (true) {
            val request = connection.receive()
            val result = executor.executeTest(request)
            connection.send(result)
        }
    }
}