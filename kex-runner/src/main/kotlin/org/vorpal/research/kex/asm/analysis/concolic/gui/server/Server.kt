package org.vorpal.research.kex.asm.analysis.concolic.gui.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.ServerSocket

class Server(port: Int) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val serverSocket = ServerSocket(port)

    private var isRunning = false

    private val clients = mutableSetOf<ClientSocket>()

    fun start() {
        if (isRunning) return

        isRunning = true
        scope.launch {
            while (isRunning) {
                val clientSocket = ClientSocket(serverSocket.accept())
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