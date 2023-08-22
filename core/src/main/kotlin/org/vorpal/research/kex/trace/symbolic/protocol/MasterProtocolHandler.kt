package org.vorpal.research.kex.trace.symbolic.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kthelper.logging.log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ControllerProtocolHandler : AutoCloseable {
    val controllerPort: Int

    fun getClient2MasterConnection(): Client2MasterConnection?
}

interface MasterProtocolHandler : AutoCloseable {
    val clientPort: Int
    val workerPort: Int

    fun receiveClientConnection(): Master2ClientConnection?
    fun receiveWorkerConnection(): Master2WorkerConnection?
}


interface Master2ClientConnection : AutoCloseable {
    fun ready(): Boolean
    fun receive(): String?
    fun send(result: String): Boolean
}

interface Master2WorkerConnection : AutoCloseable {
    fun ready(): Boolean
    fun send(request: String): Boolean
    fun receive(): String?
}

interface Client2MasterConnection : AutoCloseable {
    fun ready(): Boolean
    fun send(request: TestExecutionRequest): Boolean
    fun receive(): ExecutionResult?
}

interface Worker2MasterConnection : AutoCloseable {
    fun connect(): Boolean
    fun ready(): Boolean
    fun receive(): TestExecutionRequest?
    fun send(result: ExecutionResult): Boolean
}

// Impl

private val connectionTimeout = kexConfig.getIntValue("executor", "connectionTimeout", 100).seconds
private val communicationTimeout = kexConfig.getIntValue("executor", "communicationTimeout", 100).seconds

private val Duration.asSocketTimeout get() = this.inWholeMilliseconds.toInt()

private inline fun <T> tryOrSocketTimeout(block: () -> T): T? = try {
    block()
} catch (_: SocketTimeoutException) {
    null
}

@ExperimentalSerializationApi
@InternalSerializationApi
class ControllerProtocolSocketHandler(val ctx: ExecutionContext) : ControllerProtocolHandler {
    private val controllerSocket = ServerSocket(0).also {
        it.soTimeout = connectionTimeout.asSocketTimeout
    }
    private val serializers = mutableMapOf<ClassManager, KexSerializer>()
    override val controllerPort: Int
        get() = controllerSocket.localPort

    override fun getClient2MasterConnection(): Client2MasterConnection? = tryOrSocketTimeout {
        val serializer = serializers.getOrPut(ctx.cm) {
            KexSerializer(
                ctx.cm,
                prettyPrint = false
            )
        }
        val socket = controllerSocket.accept()
        socket.soTimeout = communicationTimeout.asSocketTimeout
        log.debug("Client {} connected to master {}", socket.localSocketAddress, socket.remoteSocketAddress)
        Client2MasterSocketConnection(serializer, socket)
    }

    override fun close() {
        controllerSocket.close()
        serializers.clear()
    }
}


class MasterProtocolSocketHandler(
    override val clientPort: Int
) : MasterProtocolHandler {
    private val workerListener = ServerSocket(0).also {
        it.soTimeout = connectionTimeout.asSocketTimeout
    }
    override val workerPort get() = workerListener.localPort

    override fun receiveClientConnection(): Master2ClientConnection? = tryOrSocketTimeout {
        val socket = Socket()
        socket.connect(InetSocketAddress("localhost", clientPort), connectionTimeout.asSocketTimeout)
        socket.soTimeout = communicationTimeout.asSocketTimeout
        log.debug("Client {} connected to master {}", socket.remoteSocketAddress, socket.localSocketAddress)
        Master2ClientSocketConnection(socket)
    }

    override fun receiveWorkerConnection(): Master2WorkerConnection? = tryOrSocketTimeout {
        val socket = workerListener.accept()
        socket.soTimeout = communicationTimeout.asSocketTimeout
        Master2WorkerSocketConnection(socket)
    }

    override fun close() {
        workerListener.close()
    }
}

class Master2ClientSocketConnection(private val socket: Socket) : Master2ClientConnection {
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): String? = tryOrSocketTimeout {
        log.debug("Receiving a message from {} to {}", socket.remoteSocketAddress, socket.localSocketAddress)
        reader.readLine().also {
            log.debug("Master received a request $it")
        }
    }

    override fun send(result: String): Boolean = tryOrSocketTimeout {
        log.debug("Master sends a response of size ${result.length}")
        writer.write(result)
        writer.newLine()
        writer.flush()
        true
    } ?: false

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

    override fun send(request: String): Boolean = tryOrSocketTimeout {
        writer.write(request)
        writer.newLine()
        writer.flush()
        true
    } ?: false

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): String? = tryOrSocketTimeout {
        reader.readLine()
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
    private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    override fun send(request: TestExecutionRequest): Boolean = tryOrSocketTimeout {
        log.debug("Client sending a request: {} from {} to {}", request, socket.localSocketAddress, socket.remoteSocketAddress)
        val json = serializer.toJson(request)
        writer.write(json)
        writer.newLine()
        writer.flush()
        log.debug("Request is sent")
        true
    } ?: false

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): ExecutionResult? = tryOrSocketTimeout {
        val json = reader.readLine()
        log.debug("Client received an answer")
        serializer.fromJson(json)
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
class Worker2MasterSocketConnection(
    val serializer: KexSerializer,
    private val port: Int
) : Worker2MasterConnection {
    private lateinit var socket: Socket
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    override fun connect(): Boolean = tryOrSocketTimeout {
        log.debug("Trying to connect to master at port $port")
        socket = Socket()
        socket.connect(InetSocketAddress("localhost", port), connectionTimeout.asSocketTimeout)
        socket.soTimeout = communicationTimeout.asSocketTimeout
        writer = socket.getOutputStream().bufferedWriter()
        reader = socket.getInputStream().bufferedReader()
        log.debug("Connected to master")
        true
    } ?: false

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun receive(): TestExecutionRequest? = tryOrSocketTimeout {
        val json = reader.readLine()
        log.debug("Received request: $json")
        serializer.fromJson(json)
    }

    override fun send(result: ExecutionResult): Boolean = tryOrSocketTimeout {
        val json = serializer.toJson(result)
        log.debug("Sending a response")
        writer.write(json)
        writer.newLine()
        writer.flush()
        true
    } ?: false

    override fun close() {
        writer.close()
        reader.close()
        socket.close()
        log.debug("Worker closed its connection")
    }

}
