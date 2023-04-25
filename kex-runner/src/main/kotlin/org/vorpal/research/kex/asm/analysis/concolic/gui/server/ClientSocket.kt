package org.vorpal.research.kex.asm.analysis.concolic.gui.server

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket


class ClientSocket(private val socket: Socket) {

    private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val output = PrintWriter(socket.getOutputStream(), true)

    fun send(message: String) {
        output.println(message)
    }

    fun receive() {
        input.readLine()
    }

    fun close() {
        input.close()
        output.close()
        socket.close()
    }
}