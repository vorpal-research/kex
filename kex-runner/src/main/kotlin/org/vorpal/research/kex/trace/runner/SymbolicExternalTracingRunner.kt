package org.vorpal.research.kex.trace.runner

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.trace.symbolic.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.Client2MasterConnection
import org.vorpal.research.kex.trace.symbolic.protocol.Client2MasterSocketConnection
import org.vorpal.research.kex.trace.symbolic.protocol.TestExecutionRequest
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getJunit
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kthelper.logging.log
import java.net.ServerSocket
import java.nio.file.Paths

@ExperimentalSerializationApi
@InternalSerializationApi
internal object ExecutorMasterController : AutoCloseable {
    private lateinit var process: Process
    private val masterPort: Int

    init {
        val tempSocket = ServerSocket(0)
        masterPort = tempSocket.localPort
        tempSocket.close()
        Thread.sleep(1000)
    }

    fun start(ctx: ExecutionContext) {
        val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
        val executorPath = (kexConfig.getPathValue(
            "executor", "executorPath"
        ) ?: Paths.get("kex-executor/target/kex-executor-0.0.1-jar-with-dependencies.jar")).toAbsolutePath()
        val executorKlass = "org.vorpal.research.kex.launcher.MasterLauncherKt"
        val executorConfigPath = (kexConfig.getPathValue(
            "executor", "executorConfigPath"
        ) ?: Paths.get("kex.ini")).toAbsolutePath()
        val masterJvmParams = kexConfig.getMultipleStringValue("executor", "masterJvmParams", ",")
        val numberOfWorkers = kexConfig.getIntValue("executor", "numberOfWorkers", 1)
        val instrumentedCodeDir = outputDir.resolve(
            kexConfig.getStringValue("output", "instrumentedDir", "instrumented")
        ).toAbsolutePath()
        val compiledCodeDir = outputDir.resolve(
            kexConfig.getStringValue("compile", "compileDir", "compiled")
        ).toAbsolutePath()
        val workerClassPath = listOfNotNull(
            executorPath,
            instrumentedCodeDir,
            compiledCodeDir,
            getIntrinsics()?.path,
            getJunit()?.path
        )

        val kfgClassPath = ctx.classPath
        val pb = ProcessBuilder(
            "java",
            "-classpath", executorPath.toString(),
            *masterJvmParams.toTypedArray(),
            executorKlass,
            "--output", "${outputDir.toAbsolutePath()}",
            "--config", "$executorConfigPath",
            "--port", "$masterPort",
            "--kfgClassPath", kfgClassPath.joinToString(getPathSeparator()),
            "--workerClassPath", workerClassPath.joinToString(getPathSeparator()),
            "--numOfWorkers", "$numberOfWorkers"
        )
        log.debug("Starting executor master process with command: '${pb.command().joinToString(" ")}'")
        process = pb.start()
    }

    fun getClientConnection(ctx: ExecutionContext): Client2MasterConnection {
        return Client2MasterSocketConnection(KexSerializer(ctx.cm, prettyPrint = false), masterPort)
    }

    override fun close() {
        process.destroy()
    }
}

class SymbolicExternalTracingRunner(val ctx: ExecutionContext) {

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun run(klass: String, setup: String, test: String): ExecutionResult {
        log.debug("Executing test $klass")

        val connection = ExecutorMasterController.getClientConnection(ctx)
        connection.use {
            it.connect()
            it.send(TestExecutionRequest(klass, test, setup))
            val result = it.receive()
            log.debug("Execution result: $result")
            return result
        }
    }
}