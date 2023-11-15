package org.vorpal.research.kex.worker

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionFailedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionTimedOutResult
import org.vorpal.research.kex.trace.symbolic.protocol.Master2ClientConnection
import org.vorpal.research.kex.trace.symbolic.protocol.Master2WorkerConnection
import org.vorpal.research.kex.trace.symbolic.protocol.MasterProtocolHandler
import org.vorpal.research.kex.util.getJvmModuleParams
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import java.nio.file.Paths

@ExperimentalSerializationApi
@InternalSerializationApi
class ExecutorMaster(
    val connection: MasterProtocolHandler,
    val kfgClassPath: List<Path>,
    val workerClassPath: List<Path>,
    private val numberOfWorkers: Int
) : Runnable {
    @Suppress("JoinDeclarationAndAssignment")
    private val workers: List<WorkerWrapper>
    private val workerQueue = Channel<WorkerWrapper>(UNLIMITED)
    private val outputDir = kexConfig.outputDirectory
    private val workerJvmParams = kexConfig.getMultipleStringValue(
        "executor", "workerJvmParams", ","
    ).toTypedArray()
    private val executorPolicyPath = kexConfig.getPathValue(
        "executor", "executorPolicyPath"
    ) { Paths.get("kex.policy") }.toAbsolutePath()
    private val executorKlass = "org.vorpal.research.kex.launcher.WorkerLauncherKt"
    private val executorConfigPath = kexConfig.getPathValue(
        "executor", "executorConfigPath"
    ) { Paths.get("kex.ini") }.toAbsolutePath()

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        prettyPrint = false
        useArrayPolymorphism = false
        classDiscriminator = "className"
        allowStructuredMapKeys = true
    }

    init {
        workers = List(numberOfWorkers) { WorkerWrapper(it) }
    }

    inner class WorkerWrapper(val id: Int) {
        private lateinit var process: Process
        private lateinit var workerConnection: Master2WorkerConnection

        init {
            reInit()
        }

        private fun reInit() = runBlocking {
            process = createProcess()
            if (this@WorkerWrapper::workerConnection.isInitialized)
                workerConnection.close()
            workerConnection = when (val tempConnection = connection.receiveWorkerConnection()) {
                null -> {
                    log.debug("Worker $id connection timeout")
                    process.destroy()
                    return@runBlocking
                }

                else -> tempConnection
            }
            log.debug("Worker $id connected")
        }

        private fun createProcess(): Process {
            val pb = ProcessBuilder(
                "java",
                *workerJvmParams,
                "-Djava.security.manager",
                "-Djava.security.policy==${executorPolicyPath}",
                "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
                *getJvmModuleParams().toTypedArray(),
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

        suspend fun processTask(clientConnection: Master2ClientConnection): Boolean {
            log.debug("Worker {} started work", id)
            while (!process.isAlive)
                reInit()

            val request = clientConnection.receive() ?: return false
            log.debug("Worker {} received request {}", id, request)

            val result = try {
                when {
                    !workerConnection.send(request) -> json.encodeToString(
                        ExecutionTimedOutResult::class.serializer(),
                        ExecutionTimedOutResult("timeout")
                    )

                    else -> when (val result = workerConnection.receive()) {
                        null -> json.encodeToString(
                            ExecutionTimedOutResult::class.serializer(),
                            ExecutionTimedOutResult("timeout")
                        )

                        else -> result
                    }
                }
            } catch (e: Throwable) {
                process.destroy()
                log.debug("Worker failed with an error", e)
                json.encodeToString(ExecutionFailedResult::class.serializer(), ExecutionFailedResult(e.message ?: ""))
            }
            log.debug("Worker $id processed result")
            return clientConnection.send(result)
        }

        fun destroy() {
            workerConnection.close()
            process.destroy()
        }
    }

    private suspend fun handleClient(clientConnection: Master2ClientConnection) = try {
        val worker = workerQueue.receive()
        log.debug("Selected a worker ${worker.id}")
        if (!worker.processTask(clientConnection)) {
            log.debug("Worker {} failed to handle client request", worker.id)
            worker.destroy()
        }
        workerQueue.send(worker)
    } catch (e: Throwable) {
        log.error("Error while working with client: ", e)
    } finally {
        clientConnection.close()
    }

    override fun run() {
        runBlocking(newFixedThreadPoolContextWithMDC(maxOf(1, numberOfWorkers / 2), "master")) {
            for (worker in workers) {
                workerQueue.send(worker)
            }

            val clientChannel = Channel<Master2ClientConnection>(numberOfWorkers)

            launch {
                while (true) {
                    val nextClient = clientChannel.receive()
                    launch { handleClient(nextClient) }
                }
            }

            while (true) {
                log.debug("Master is waiting for clients")
                val client = connection.receiveClientConnection() ?: continue
                log.debug("Master received a client connection")
                clientChannel.send(client)
                log.debug("Master sent a client to handler")
            }
        }
    }

    fun destroy() {
        for (worker in workers) {
            worker.destroy()
        }
    }
}
