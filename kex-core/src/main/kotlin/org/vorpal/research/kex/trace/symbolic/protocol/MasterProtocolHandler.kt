package org.vorpal.research.kex.trace.symbolic.protocol

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kthelper.logging.log
import kotlin.time.Duration.Companion.seconds

interface ControllerProtocolHandler : AutoCloseable {
    val controllerPort: Int
    val masterPort: Int

    suspend fun init(): Boolean

    suspend fun getClient2MasterConnection(): Client2MasterConnection?
}

interface MasterProtocolHandler : AutoCloseable {
    val controllerPort: Int
    val clientPort: Int
    val workerPort: Int

    suspend fun receiveClientConnection(): Master2ClientConnection?
    suspend fun receiveWorkerConnection(): Master2WorkerConnection?
}


interface Master2ClientConnection : AutoCloseable {
    suspend fun ready(): Boolean
    suspend fun receive(): String?
    suspend fun send(result: String): Boolean
}

interface Master2WorkerConnection : AutoCloseable {
    suspend fun ready(): Boolean
    suspend fun send(request: String): Boolean
    suspend fun receive(): String?
}

interface Client2MasterConnection : AutoCloseable {
    suspend fun ready(): Boolean
    suspend fun send(request: TestExecutionRequest): Boolean
    suspend fun receive(): ExecutionResult?
}

interface Worker2MasterConnection : AutoCloseable {
    suspend fun connect(): Boolean
    suspend fun ready(): Boolean
    suspend fun receive(): TestExecutionRequest?
    suspend fun send(result: ExecutionResult): Boolean
}

// Impl

private val connectionTimeout = kexConfig.getIntValue("executor", "connectionTimeout", 100).seconds
private val communicationTimeout = kexConfig.getIntValue("executor", "communicationTimeout", 100).seconds

@ExperimentalSerializationApi
@InternalSerializationApi
class ControllerProtocolSocketHandler(val ctx: ExecutionContext) : ControllerProtocolHandler {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val controllerSocket = aSocket(selectorManager).tcp().bind()
    private val serializers = mutableMapOf<ClassManager, KexSerializer>()
    private lateinit var masterConnection: Socket
    override val controllerPort: Int
        get() = controllerSocket.localAddress.toJavaAddress().port
    override var masterPort: Int = 0
        private set

    override suspend fun init(): Boolean = withTimeoutOrNull(connectionTimeout) {
        log.debug("Controller is waiting for command from master")
        val serializer = serializers.getOrPut(ctx.cm) {
            KexSerializer(
                ctx.cm,
                prettyPrint = false
            )
        }
        masterConnection = controllerSocket.accept()
        val command = masterConnection.openReadChannel().readUTF8Line()!!
        masterPort = serializer.fromJson<PortCommand>(command).port
        log.debug("Controller received master port $masterPort")
        true
    } ?: false

    override suspend fun getClient2MasterConnection(): Client2MasterConnection? =
        withTimeoutOrNull(connectionTimeout) {
            val serializer = serializers.getOrPut(ctx.cm) {
                KexSerializer(
                    ctx.cm,
                    prettyPrint = false
                )
            }
            val socket = aSocket(selectorManager).tcp().connect("localhost", masterPort)
            log.debug("Client {} connected to master {}", socket.localAddress, socket.remoteAddress)
            Client2MasterSocketConnection(serializer, socket)
        }

    override fun close() {
        controllerSocket.close()
        masterConnection.close()
        serializers.clear()
    }
}


class MasterProtocolSocketHandler(
    override val controllerPort: Int
) : MasterProtocolHandler {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val clientListener = aSocket(selectorManager).tcp().bind()
    private val workerListener = aSocket(selectorManager).tcp().bind()
    private var controllerConnection: Socket

    override val clientPort get() = clientListener.localAddress.toJavaAddress().port
    override val workerPort get() = workerListener.localAddress.toJavaAddress().port

    init {
        runBlocking {
            log.debug("Master is connecting to controller")
            controllerConnection = aSocket(selectorManager).tcp().connect("localhost", controllerPort)
            val channel = controllerConnection.openWriteChannel()
            log.debug("Master sent command \"{\"port\":$clientPort}\" to controller")
            channel.writeStringUtf8("{\"port\":$clientPort}\n")
            channel.close()
        }
    }

    override suspend fun receiveClientConnection(): Master2ClientConnection? = withTimeoutOrNull(connectionTimeout) {
        val socket = clientListener.accept()
        log.debug("Client {} connected to master {}", socket.remoteAddress, socket.localAddress)
        Master2ClientSocketConnection(socket)
    }

    override suspend fun receiveWorkerConnection(): Master2WorkerConnection? = withTimeoutOrNull(connectionTimeout) {
        val socket = workerListener.accept()
        Master2WorkerSocketConnection(socket)
    }

    override fun close() {
        controllerConnection.close()
        clientListener.close()
        workerListener.close()
    }
}

class Master2ClientSocketConnection(private val socket: Socket) : Master2ClientConnection {
    private val writer = socket.openWriteChannel(autoFlush = true)
    private val reader = socket.openReadChannel()

    override suspend fun ready(): Boolean {
        return reader.availableForRead > 0
    }

    override suspend fun receive(): String? = withTimeoutOrNull(communicationTimeout) {
        log.debug("Receiving a message from {} to {}", socket.remoteAddress, socket.localAddress)
        reader.readUTF8Line().also {
            log.debug("Master received a request $it")
        }
    }

    override suspend fun send(result: String): Boolean = withTimeoutOrNull(communicationTimeout) {
        log.debug("Master sends a response of size ${result.length}")
        try {
            writer.writeStringUtf8(result + "\n")
        } catch (e: Throwable) {
            log.error("Master received exception $e")
        }
        true
    } ?: false

    override fun close() {
        writer.close()
        socket.close()
        log.debug("Master closed its connection to client")
    }
}

class Master2WorkerSocketConnection(private val socket: Socket) : Master2WorkerConnection {
    private val writer = socket.openWriteChannel(autoFlush = true)
    private val reader = socket.openReadChannel()

    override suspend fun send(request: String): Boolean = withTimeoutOrNull(communicationTimeout) {
        writer.writeStringUtf8(request + "\n")
        true
    } ?: false

    override suspend fun ready(): Boolean {
        return reader.availableForRead > 0
    }

    override suspend fun receive(): String? = withTimeoutOrNull(communicationTimeout) {
        reader.readUTF8Line()
    }

    override fun close() {
        writer.close()
        socket.close()
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
class Client2MasterSocketConnection(
    val serializer: KexSerializer,
    private val socket: Socket
) : Client2MasterConnection {
    private val writer = socket.openWriteChannel(autoFlush = true)
    private val reader = socket.openReadChannel()

    override suspend fun send(request: TestExecutionRequest): Boolean = withTimeoutOrNull(communicationTimeout) {
        log.debug("Client sending a request: {} from {} to {}", request, socket.localAddress, socket.remoteAddress)
        val json = serializer.toJson(request)
        writer.writeStringUtf8(json + "\n")
        log.debug("Request is sent")
        true
    } ?: false

    override suspend fun ready(): Boolean {
        return reader.availableForRead > 0
    }

    override suspend fun receive(): ExecutionResult? = withTimeoutOrNull(communicationTimeout) {
        val json = reader.readUTF8Line() ?: return@withTimeoutOrNull null
        log.debug("Client received an answer")
        serializer.fromJson(json)
    }

    override fun close() {
        writer.close()
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
    private lateinit var writer: ByteWriteChannel
    private lateinit var reader: ByteReadChannel

    override suspend fun connect(): Boolean = withTimeoutOrNull(connectionTimeout) {
        log.debug("Trying to connect to master at port $port")
        socket = aSocket(SelectorManager(Dispatchers.IO)).tcp()
            .connect("localhost", port)
        writer = socket.openWriteChannel(autoFlush = true)
        reader = socket.openReadChannel()
        log.debug("Connected to master")
        true
    } ?: false

    override suspend fun ready(): Boolean {
        return reader.availableForRead > 0
    }

    override suspend fun receive(): TestExecutionRequest? = withTimeoutOrNull(communicationTimeout) {
        val json = reader.readUTF8Line() ?: return@withTimeoutOrNull null
        log.debug("Received request: $json")
        serializer.fromJson(json)
    }

    override suspend fun send(result: ExecutionResult): Boolean = withTimeoutOrNull(communicationTimeout) {
        val json = serializer.toJson(result)
        log.debug("Sending a response")
        writer.writeStringUtf8(json + "\n")
        true
    } ?: false

    override fun close() {
        writer.close()
        socket.close()
        log.debug("Worker closed its connection")
    }

}
