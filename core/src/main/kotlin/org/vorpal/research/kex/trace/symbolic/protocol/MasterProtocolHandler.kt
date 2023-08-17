package org.vorpal.research.kex.trace.symbolic.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kthelper.logging.log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.time.Duration

interface MasterProtocolHandler {
    val clientPort: Int
    val workerPort: Int

    fun receiveClientConnection(): Master2ClientConnection
    fun receiveWorkerConnection(timeout: Duration): Master2WorkerConnection
}


interface Master2ClientConnection : AutoCloseable {
    fun ready(): Boolean
    fun receive(): String
    fun send(result: String)
}

interface Master2WorkerConnection : AutoCloseable {
    fun ready(): Boolean
    fun send(request: String)
    fun receive(): String
}

interface Client2MasterConnection : AutoCloseable {
    fun connect(timeout: Duration): Boolean
    fun ready(): Boolean
    fun send(request: TestExecutionRequest)
    fun receive(): ExecutionResult
}

interface Worker2MasterConnection : AutoCloseable {
    fun connect(timeout: Duration): Boolean
    fun ready(): Boolean
    fun receive(): TestExecutionRequest
    fun send(result: ExecutionResult)
}

// Impl

class MasterProtocolSocketHandler(
    override val clientPort: Int
) : MasterProtocolHandler {
    private val workerListener = ServerSocket(0)
    override val workerPort get() = workerListener.localPort

    override fun receiveClientConnection(): Master2ClientConnection {
        val socket = Socket("localhost", clientPort)
        return Master2ClientSocketConnection(socket)
    }

    override fun receiveWorkerConnection(timeout: Duration): Master2WorkerConnection {
        val socket = workerListener.accept()
        socket.soTimeout = timeout.inWholeMilliseconds.toInt()
        return Master2WorkerSocketConnection(socket)
    }
}

class Master2ClientSocketConnection(private val socket: Socket) : Master2ClientConnection {
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): String {
        while (!reader.ready()) {
            log.debug("Waiting, reader is not ready yet")
        }
        return reader.readLine().also {
            log.debug("Master received a request $it")
        }
    }

    override fun send(result: String) {
        log.debug("Master sends a response")
        writer.write(result)
        writer.newLine()
        writer.flush()
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
        log.debug("Master closed its connection to client")
    }
}

class Master2WorkerSocketConnection(private val socket: Socket) : Master2WorkerConnection {
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    override fun send(request: String) {
        writer.write(request)
        writer.newLine()
        writer.flush()
    }

    override fun ready(): Boolean {
        return reader.ready()
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
class Client2MasterSocketConnection(
    val serializer: KexSerializer,
    private val socket: Socket
) : Client2MasterConnection {
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    override fun connect(timeout: Duration): Boolean {
        writer = socket.getOutputStream().bufferedWriter()
        reader = socket.getInputStream().bufferedReader()
        log.debug("Connected to master")
        return true
    }

    override fun send(request: TestExecutionRequest) {
        log.debug("Client sending a request: {}", request)
        val json = serializer.toJson(request)
        writer.write(json)
        writer.newLine()
        writer.flush()
        log.debug("Request is sent")
    }

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): ExecutionResult {
        val json = reader.readLine()
        log.debug("Client received an answer")
        return serializer.fromJson(json)
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
        log.debug("Client closed its connection")
    }
}


@ExperimentalSerializationApi
@InternalSerializationApi
class Worker2MasterSocketConnection(val serializer: KexSerializer, private val port: Int) : Worker2MasterConnection {
    private lateinit var socket: Socket
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    override fun connect(timeout: Duration): Boolean {
        log.debug("Trying to connect to master at port $port")
        socket = Socket()
        try {
            socket.connect(InetSocketAddress("localhost", port), timeout.inWholeMilliseconds.toInt())
        } catch (_: SocketTimeoutException) {
            log.error("Could not connect to master, timeout exception")
            return false
        }
        writer = socket.getOutputStream().bufferedWriter()
        reader = socket.getInputStream().bufferedReader()
        log.debug("Connected to master")
        return true
    }

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): TestExecutionRequest {
        val json = reader.readLine()
        log.debug("Received request: $json")
        return serializer.fromJson(json)
    }

    override fun send(result: ExecutionResult) {
        val json = serializer.toJson(result)
        log.debug("Sending a response")
        writer.write(json)
        writer.newLine()
        writer.flush()
    }

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
        log.debug("Worker closed its connection")
    }

}
