package org.vorpal.research.kex.trace.symbolic.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.trace.symbolic.ExecutionResult
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket

interface MasterProtocolHandler {
    val clientPort: Int
    val workerPort: Int

    fun receiveClientConnection(): Master2ClientConnection
    fun receiveWorkerConnection(): Master2WorkerConnection
}


interface Master2ClientConnection : AutoCloseable {
    fun receive(): String
    fun send(result: String)
}

interface Master2WorkerConnection : AutoCloseable {
    fun send(request: String)
    fun receive(): String
}

interface Client2MasterConnection : AutoCloseable {
    fun connect(): Boolean
    fun send(request: TestExecutionRequest)
    fun receive(): ExecutionResult
}

interface Worker2MasterConnection : AutoCloseable {
    fun connect(): Boolean
    fun receive(): TestExecutionRequest
    fun send(result: ExecutionResult)
}

// Impl

class MasterProtocolSocketHandler(
    clientPort: Int
) : MasterProtocolHandler {
    private val clientListener = ServerSocket(clientPort)
    private val workerListener = ServerSocket()

    override val clientPort get() = clientListener.localPort
    override val workerPort get() = workerListener.localPort

    override fun receiveClientConnection(): Master2ClientConnection {
        val socket = clientListener.accept()
        return Master2ClientSocketConnection(socket)
    }

    override fun receiveWorkerConnection(): Master2WorkerConnection {
        val socket = workerListener.accept()
        return Master2WorkerSocketConnection(socket)
    }
}

class Master2ClientSocketConnection(private val socket: Socket) : Master2ClientConnection {
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    override fun receive(): String {
        return reader.readLine()
    }

    override fun send(result: String) {
        writer.write(result)
        writer.newLine()
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
    }
}

class Master2WorkerSocketConnection(private val socket: Socket) : Master2WorkerConnection {
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    override fun send(request: String) {
        writer.write(request)
        writer.newLine()
    }

    override fun receive(): String {
        return reader.readLine()
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
class Client2MasterSocketConnection(val serializer: KexSerializer, private val port: Int) : Client2MasterConnection {
    private lateinit var socket: Socket
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    override fun connect(): Boolean {
        socket = Socket("localhost", port)
        writer = socket.getOutputStream().bufferedWriter()
        reader = socket.getInputStream().bufferedReader()
        return true
    }

    override fun send(request: TestExecutionRequest) {
        val json = serializer.toJson(request)
        writer.write(json)
        writer.newLine()
    }

    override fun receive(): ExecutionResult {
        val json = reader.readLine()
        return serializer.fromJson(json)
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
    }
}


@ExperimentalSerializationApi
@InternalSerializationApi
class Worker2MasterSocketConnection(val serializer: KexSerializer, private val port: Int) : Worker2MasterConnection {
    private lateinit var socket: Socket
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    override fun connect(): Boolean {
        socket = Socket("localhost", port)
        writer = socket.getOutputStream().bufferedWriter()
        reader = socket.getInputStream().bufferedReader()
        return true
    }

    override fun receive(): TestExecutionRequest {
        val json = reader.readLine()
        return serializer.fromJson(json)
    }

    override fun send(result: ExecutionResult) {
        val json = serializer.toJson(result)
        writer.write(json)
        writer.newLine()
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
    }

}