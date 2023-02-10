package org.vorpal.research.kex.worker

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.ExecutionFailedResult
import org.vorpal.research.kex.trace.symbolic.ExecutionTimedOutResult
import org.vorpal.research.kex.trace.symbolic.protocol.Master2ClientConnection
import org.vorpal.research.kex.trace.symbolic.protocol.Master2WorkerConnection
import org.vorpal.research.kex.trace.symbolic.protocol.MasterProtocolHandler
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kthelper.logging.log
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ArrayBlockingQueue
import kotlin.time.Duration.Companion.seconds

@ExperimentalSerializationApi
@InternalSerializationApi
class ExecutorMaster(
    val connection: MasterProtocolHandler,
    val kfgClassPath: List<Path>,
    val workerClassPath: List<Path>,
    numberOfWorkers: Int
) : Runnable {
    private val timeout = kexConfig.getIntValue("runner", "timeout", 100)
    private val workerQueue = ArrayBlockingQueue<WorkerWrapper>(numberOfWorkers)
    private val outputDir = kexConfig.outputDirectory
    private val workerJvmParams = kexConfig.getMultipleStringValue("executor", "workerJvmParams", ",").toTypedArray()
    private val executorPolicyPath = (kexConfig.getPathValue(
        "executor", "executorPolicyPath"
    ) ?: Paths.get("kex.policy")).toAbsolutePath()
    private val executorKlass = "org.vorpal.research.kex.launcher.WorkerLauncherKt"
    private val executorConfigPath = (kexConfig.getPathValue(
        "executor", "executorConfigPath"
    ) ?: Paths.get("kex.ini")).toAbsolutePath()


    init {
        repeat(numberOfWorkers) {
            workerQueue.add(WorkerWrapper(it))
        }
    }

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        prettyPrint = false
        useArrayPolymorphism = false
        classDiscriminator = "className"
        allowStructuredMapKeys = true
    }

    inner class WorkerWrapper(val id: Int) {
        private lateinit var process: Process
        private lateinit var workerConnection: Master2WorkerConnection

        init {
            reInit()
        }

        private fun reInit() {
            synchronized(connection) {
                process = createProcess()
                if (this::workerConnection.isInitialized)
                    workerConnection.close()
                workerConnection = connection.receiveWorkerConnection(timeout.seconds)
                log.debug("Worker $id connected")
            }
        }

        private fun createProcess(): Process {
            val pb = ProcessBuilder(
                "java",
                *workerJvmParams,
                "-Djava.security.manager",
                "-Djava.security.policy==${executorPolicyPath}",
                "-classpath", workerClassPath.joinToString(getPathSeparator()),
                executorKlass,
                "--output", "${outputDir.toAbsolutePath()}",
                "--config", executorConfigPath.toString(),
                "--option", "kex:log:${outputDir.resolve("kex-executor-worker$id.log").toAbsolutePath()}",
                "--classpath", kfgClassPath.joinToString(getPathSeparator()),
                "--port", "${connection.workerPort}"
            )
            log.debug("Starting worker process with command: '${pb.command().joinToString(" ")}'")
            return pb.start()
        }

        fun processTask(clientConnection: Master2ClientConnection) {
            while (!process.isAlive)
                reInit()

            val request = clientConnection.receive()
            log.debug("Worker $id receiver request $request")
            workerConnection.send(request)
            val result = try {
                workerConnection.receive()
            } catch (e: SocketTimeoutException) {
                process.destroy()
                log.debug("Received socket timeout exception")
                json.encodeToString(ExecutionTimedOutResult::class.serializer(), ExecutionTimedOutResult("timeout"))
            } catch (e: Throwable) {
                process.destroy()
                log.debug("Worker failed with an error", e)
                json.encodeToString(ExecutionFailedResult::class.serializer(), ExecutionFailedResult(e.message ?: ""))
            }
            log.debug("Worker $id processed result")
            clientConnection.send(result)
        }

        fun destroy() {
            log.debug("Worker $id is destroyed")
            process.destroy()
        }
    }

    private fun handleClient(clientConnection: Master2ClientConnection) = try {
        val worker = workerQueue.take()
        log.debug("Selected a worker ${worker.id}")
        worker.processTask(clientConnection)
        workerQueue.add(worker)
    } catch (e: Throwable) {
        log.error("Error while working with client: ", e)
    }

    override fun run() {
        runBlocking {
            while (true) {
                log.debug("Master is waiting for clients")
                val client = connection.receiveClientConnection()
                log.debug("Master received a client connection")
                launch {
                    client.use { handleClient(it) }
                }
                yield()
            }
        }
    }

    fun destroy() {
        while (workerQueue.isNotEmpty()) {
            val worker = workerQueue.poll()
            worker.destroy()
        }
    }

}
