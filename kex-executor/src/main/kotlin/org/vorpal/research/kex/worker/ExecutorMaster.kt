package org.vorpal.research.kex.worker

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.protocol.Master2ClientConnection
import org.vorpal.research.kex.trace.symbolic.protocol.Master2WorkerConnection
import org.vorpal.research.kex.trace.symbolic.protocol.MasterProtocolHandler
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ArrayBlockingQueue

class ExecutorMaster(
    val connection: MasterProtocolHandler,
    val kfgClassPath: List<Path>,
    val workerClassPath: List<Path>,
    numberOfWorkers: Int
) : Runnable {
    private val timeout = kexConfig.getLongValue("runner", "timeout", 10000L)
    private val workerQueue = ArrayBlockingQueue<WorkerWrapper>(numberOfWorkers)
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
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

    inner class WorkerWrapper(val id: Int) {
        private lateinit var process: Process
        private lateinit var workerConnection: Master2WorkerConnection

        init {
            reInit()
        }

        private fun reInit() {
            synchronized(connection) {
                process = createProcess()
                workerConnection = connection.receiveWorkerConnection()
                log.debug("Worker $id connected")
            }
        }

        private fun createProcess(): Process {
            val pb = ProcessBuilder(
                "java",
                "-Djava.security.manager",
                "-Djava.security.policy==${executorPolicyPath}",
                "-classpath", workerClassPath.joinToString(getPathSeparator()),
                executorKlass,
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
            val result = workerConnection.receive()
            log.debug("Worker $id processed result: $result")
            clientConnection.send(result)
        }
    }

    private fun handleClient(clientConnection: Master2ClientConnection) {
        val worker = workerQueue.poll()
        log.debug("Selected a worker ${worker.id}")
        worker.processTask(clientConnection)
        workerQueue.add(worker)
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

}