package org.vorpal.research.kex.asm.analysis.concolic.gui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.asm.analysis.concolic.gui.server.Server
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kfg.ir.Method

class GraphServerProxySelector(private val concolicPathSelector: ConcolicPathSelector) : ConcolicPathSelector {

    override val ctx = concolicPathSelector.ctx

    companion object {
        private val server = Server(port = 8080)
    }

    init {
        server.start()
    }

    private val graph = Graph()

    override suspend fun isEmpty(): Boolean = concolicPathSelector.isEmpty()

    override suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult) {
        val vertices = graph.addTrace(result.trace.toPersistentState())
        server.broadcast(Json.encodeToString(vertices))
        concolicPathSelector.addExecutionTrace(method, result)
    }

    override suspend fun hasNext(): Boolean = concolicPathSelector.hasNext()

    override suspend fun next(): PersistentSymbolicState = concolicPathSelector.next()
}