package org.vorpal.research.kex.worker

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.launcher.WorkerLauncher
import org.vorpal.research.kex.trace.symbolic.protocol.Master2ClientConnection
import org.vorpal.research.kex.trace.symbolic.protocol.Master2WorkerConnection
import org.vorpal.research.kex.trace.symbolic.protocol.MasterProtocolHandler
import org.vorpal.research.kex.util.getPathSeparator
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ArrayBlockingQueue

class ExecutorMaster(
    val connection: MasterProtocolHandler,
    val kfgClassPath: List<Path>,
    val workerClassPath: List<Path>,
    numberOfWorkers: Int
) : Runnable {
    private val workerQueue = ArrayBlockingQueue<WorkerWrapper>(numberOfWorkers)
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    private val executorPolicyPath = (kexConfig.getPathValue(
        "executor", "executorPolicyPath"
    ) ?: Paths.get("kex.policy")).toAbsolutePath()
    private val executorKlass = WorkerLauncher::class.qualifiedName
    private val executorConfigPath = (kexConfig.getPathValue(
        "executor", "executorConfigPath"
    ) ?: Paths.get("kex.ini")).toAbsolutePath()


    init {
        repeat(numberOfWorkers) {
            workerQueue.add(WorkerWrapper(it))
        }
    }

    inner class WorkerWrapper(private val workerId: Int) {
        private lateinit var process: Process
        private lateinit var workerConnection: Master2WorkerConnection

        init {
            reInit()
        }

        private fun reInit() {
            synchronized(connection) {
                process = createProcess()
                workerConnection = connection.receiveWorkerConnection()
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
                "--option", "kex:log:${outputDir.resolve("kex-executor-worker$workerId.log").toAbsolutePath()}",
                "--classpath", kfgClassPath.joinToString(getPathSeparator()),
                "--port", "${connection.workerPort}"
            )
            return pb.start()
        }

        fun processTask(clientConnection: Master2ClientConnection) {
            while (!process.isAlive)
                reInit()

            val request = clientConnection.receive()
            workerConnection.send(request)
            val result = workerConnection.receive()
            clientConnection.send(result)
        }
    }

    private fun handleClient(clientConnection: Master2ClientConnection) {
        val worker = workerQueue.poll()
        worker.processTask(clientConnection)
        workerQueue.add(worker)
    }

    override fun run() {
        runBlocking {
            while (true) {
                val client = connection.receiveClientConnection()
                launch {
                    client.use { handleClient(it) }
                }
            }
        }
    }

}