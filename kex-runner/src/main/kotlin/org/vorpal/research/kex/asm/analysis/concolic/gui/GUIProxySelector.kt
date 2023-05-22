package org.vorpal.research.kex.asm.analysis.concolic.gui

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.logging.log
import java.net.ServerSocket

class GUIProxySelector(private val concolicPathSelector: ConcolicPathSelector) : ConcolicPathSelector {

    override val ctx = concolicPathSelector.ctx

    private companion object {
        val server = ServerSocket(8080)
        // TODO: server.soTimeout = ? (+ create Server wrapper); move client to instance level?
        val client = Client(server.accept())
    }

    init {
        logDebugGUI("Init")
        client.send("INIT")
    }

    private val graph = Graph()

    override suspend fun isEmpty(): Boolean = concolicPathSelector.isEmpty()

    override suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult) {
        val vertices = graph.addTrace(result.trace.toPersistentState())
        val json = Json.encodeToString(vertices)
        logDebugGUI("Vertices trace: $json")
        logDebugGUI("$graph")
        client.send(json)
        concolicPathSelector.addExecutionTrace(method, result)
    }

    override suspend fun next(): PersistentSymbolicState {
        logDebugGUI("Waiting for client decision")
        val received = client.receive()

        if (received == null || received == "NEXT") {
            logDebugGUI("Client sent 'NEXT' or closed its connection")
            return concolicPathSelector.next()
        }

        val vertex: Vertex = Json.decodeFromString(received)
        logDebugGUI("Received vertex: $vertex")

        return interactiveNext(vertex) ?: concolicPathSelector.next()
    }

    private fun interactiveNext(vertex: Vertex): PersistentSymbolicState? {
        val pathClause = graph.findClauseByVertex(vertex) as? PathClause ?: return null
        val state = graph.findStateByPathClause(pathClause) ?: return null

        val revertedClause = reverse(pathClause) ?: return null
        val clauses = state.clauses.subState(0, state.clauses.indexOf(pathClause))
        val path = state.path.subPath(0, state.path.indexOf(pathClause))

        return persistentSymbolicState(
            clauses,
            path + revertedClause,
            state.concreteValueMap,
            state.termMap
        )
    }

    override suspend fun hasNext(): Boolean = concolicPathSelector.hasNext()

    override fun reverse(pathClause: PathClause): PathClause? = concolicPathSelector.reverse(pathClause)

    private fun logDebugGUI(s: String) {
        log.debug("[GUI] $s")
    }
}