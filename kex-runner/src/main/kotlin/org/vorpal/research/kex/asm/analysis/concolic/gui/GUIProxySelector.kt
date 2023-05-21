package org.vorpal.research.kex.asm.analysis.concolic.gui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.asm.analysis.concolic.gui.server.ClientSocket
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.log
import java.net.ServerSocket

class GUIProxySelector(private val concolicPathSelector: ConcolicPathSelector) : ConcolicPathSelector {

    override val ctx = concolicPathSelector.ctx

    private companion object {
        val server = ServerSocket(8080)
        // TODO: server.soTimeout = ? (+ create Server wrapper)
        val clientSocket = ClientSocket(server.accept())
    }

    init {
        logDebugGUI("Init")
        clientSocket.send("INIT")
    }

    private val graph = Graph()

    override suspend fun isEmpty(): Boolean = concolicPathSelector.isEmpty()

    override suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult) {
        val vertices = graph.addTrace(result.trace.toPersistentState())
        val json = Json.encodeToString(vertices)
        logDebugGUI("Vertices trace: $json")
        logDebugGUI("$graph")
        clientSocket.send(json)
        concolicPathSelector.addExecutionTrace(method, result)
    }

    override suspend fun next(): PersistentSymbolicState = concolicPathSelector.next()

    override suspend fun hasNext(): Boolean = concolicPathSelector.hasNext()

    override fun reverse(pathClause: PathClause): PathClause? = concolicPathSelector.reverse(pathClause)

    private fun logDebugGUI(s: String) {
        log.debug("[GUI] $s")
    }
}