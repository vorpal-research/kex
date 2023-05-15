package org.vorpal.research.kex.asm.analysis.concolic.gui.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.vorpal.research.kthelper.logging.log
import java.net.ServerSocket

@Deprecated("Multiple clients support is redundant")
class Server(port: Int) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val serverSocket = ServerSocket(port)

    private var isRunning = false

    private val clients = mutableSetOf<ClientSocket>()

    fun start() {
        if (isRunning) {
            log.debug("GUI Server is already running")
            return
        }

        log.debug("Start GUI Server at port ${serverSocket.localPort}")
        isRunning = true
        scope.launch {
            while (isRunning) {
                val clientSocket = ClientSocket(serverSocket.accept())
                clientSocket.send("Handshake")
                clients.add(clientSocket)
                launch {
                    try {
                        clientSocket.receive()
                    } catch (_: Exception) {
                        // Client closed its connection
                    } finally {
                        clientSocket.close()
                        clients.remove(clientSocket)
                    }
                }
            }
        }
    }

    fun broadcast(message: String) {
        if (!isRunning) return
        log.debug("Broadcasting to ${clients.size} client(s): $message")
        clients.forEach {
            it.send(message)
        }
    }

    fun shutdown() {
        isRunning = false
        clients.forEach { it.close() }
        serverSocket.close()
//        scope.cancel()
    }
}