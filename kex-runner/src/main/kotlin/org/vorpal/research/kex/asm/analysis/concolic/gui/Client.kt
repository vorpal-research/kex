package org.vorpal.research.kex.asm.analysis.concolic.gui

import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class Client(private val socket: Socket) : AutoCloseable {

    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val writer = PrintWriter(socket.getOutputStream(), true)

    fun send(message: String): Boolean = `try` {
        writer.println(message)
        true
    }.getOrDefault(false)

    fun receive(): String? = tryOrNull { reader.readLine() }

    override fun close() {
        reader.close()
        writer.close()
        socket.close()
    }
}
