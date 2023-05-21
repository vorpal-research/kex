package org.vorpal.research.kex.asm.analysis.concolic.gui

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket


class Client(private val socket: Socket) {

    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val writer = PrintWriter(socket.getOutputStream(), true)

    fun send(message: String): Boolean = try {
        writer.println(message)
        true
    } catch (_: Exception) {
        false
    }

    fun receive(): String? = try {
        reader.readLine()
    } catch (_: Exception) {
        null
    }

    fun close() {
        reader.close()
        writer.close()
        socket.close()
    }
}