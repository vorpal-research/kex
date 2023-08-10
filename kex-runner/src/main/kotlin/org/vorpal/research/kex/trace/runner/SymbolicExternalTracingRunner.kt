package org.vorpal.research.kex.trace.runner

import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.trace.symbolic.protocol.Client2MasterConnection
import org.vorpal.research.kex.trace.symbolic.protocol.Client2MasterSocketConnection
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionTimedOutResult
import org.vorpal.research.kex.trace.symbolic.protocol.TestExecutionRequest
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getJunit
import org.vorpal.research.kex.util.getJvmModuleParams
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kthelper.logging.log
import java.net.ServerSocket
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

@ExperimentalSerializationApi
@InternalSerializationApi
internal object ExecutorMasterController : AutoCloseable {
    private lateinit var process: Process
    private val masterPort: Int
    private val serializers = mutableMapOf<ClassManager, KexSerializer>()

    init {
        val tempSocket = ServerSocket(0)
        masterPort = tempSocket.localPort
        tempSocket.close()
        // this is fucked up
        Thread.sleep(2000)
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            if (process.isAlive) process.destroy()
        })
    }

    fun start(ctx: ExecutionContext) {
        val outputDir = kexConfig.outputDirectory
        val executorPath = kexConfig.getPathValue("executor", "executorPath") {
            Paths.get("kex-executor/target/kex-executor-0.0.1-jar-with-dependencies.jar")
        }.toAbsolutePath()
        val executorKlass = "org.vorpal.research.kex.launcher.MasterLauncherKt"
        val executorConfigPath = kexConfig.getPathValue("executor", "executorConfigPath") {
            Paths.get("kex.ini")
        }.toAbsolutePath()
        val executorPolicyPath = kexConfig.getPathValue("executor", "executorPolicyPath") {
            Paths.get("kex.policy")
        }.toAbsolutePath()
        val masterJvmParams = kexConfig.getMultipleStringValue("executor", "masterJvmParams", ",").toTypedArray()
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
            "-Djava.security.manager", "-Djava.security.policy==${executorPolicyPath}",
            "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
            *getJvmModuleParams().toTypedArray(),
            *masterJvmParams,
            "-classpath", executorPath.toString(),
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
        Thread.sleep(1000)
    }

    fun getClientConnection(ctx: ExecutionContext): Client2MasterConnection {
        return Client2MasterSocketConnection(serializers.getOrPut(ctx.cm) {
            KexSerializer(
                ctx.cm,
                prettyPrint = false
            )
        }, masterPort)
    }

    override fun close() {
        process.destroy()
    }
}

class SymbolicExternalTracingRunner(val ctx: ExecutionContext) {
    private val timeout = kexConfig.getIntValue("runner", "timeout", 100).seconds

    @ExperimentalSerializationApi
    @InternalSerializationApi
    suspend fun run(klass: String, setup: String, test: String): ExecutionResult {
        log.debug("Executing test $klass")

        val connection = ExecutorMasterController.getClientConnection(ctx)
        connection.use {
            if (!it.connect(timeout)) return ExecutionTimedOutResult("Could not connect")
            it.send(TestExecutionRequest(klass, test, setup))
            while (!it.ready()) {
                yield()
            }
            val result = it.receive()
            when (result) {
                is ExecutionCompletedResult -> log.debug("Execution result: {}", result.trace)
                else -> log.debug("Execution result: {}", result)
            }
            return result
        }
    }
}
