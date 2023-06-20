package org.vorpal.research.kex.asm.analysis.concolic.gui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.asm.analysis.concolic.ConcolicPathSelector
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.PersistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.persistentSymbolicState
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.toPersistentState
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import java.net.ServerSocket

class GUIProxySelector(private val concolicPathSelector: ConcolicPathSelector) : ConcolicPathSelector {

    override val ctx = concolicPathSelector.ctx

    private companion object {
        val server = ServerSocket(kexConfig.getIntValue("gui", "serverPort", 8080)).apply {
            soTimeout = kexConfig.getIntValue("gui", "serverTimeout", 10) * 1000
        }
    }

    private val client = `try` {
        log.debug("Waiting for client connection")
        Client(server.accept()).apply {
            send("INIT")
            log.debug("Client is connected")
        }
    }.getOrElse {
        unreachable { log.error("Connection timeout has expired") }
    }

    private val graph = Graph()

    override suspend fun isEmpty(): Boolean = concolicPathSelector.isEmpty()

    override suspend fun addExecutionTrace(method: Method, result: ExecutionCompletedResult) {
        val vertices = graph.addTrace(result.trace.toPersistentState())
        val json = Json.encodeToString(vertices)
        log.debug("Vertices trace: $json")
        log.debug(graph)
        client.send(json)
        concolicPathSelector.addExecutionTrace(method, result)
    }

    override suspend fun next(): PersistentSymbolicState {
        log.debug("Waiting for client decision")
        val received = client.receive()

        if (received == null || received == "NEXT") {
            log.debug("Client sent 'NEXT' or closed its connection")
            return concolicPathSelector.next()
        }

        val vertex: Vertex = Json.decodeFromString(received)
        log.debug("Received vertex: {}", vertex)

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

    override suspend fun hasNext(): Boolean = concolicPathSelector.hasNext().also {
        if (!it) {
            log.debug("Closing client connection...")
            client.close()
            log.debug("Client connection is closed")
        }
    }

    override fun reverse(pathClause: PathClause): PathClause? = concolicPathSelector.reverse(pathClause)

}
