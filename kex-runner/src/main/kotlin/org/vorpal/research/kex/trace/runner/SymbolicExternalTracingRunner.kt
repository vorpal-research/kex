package org.vorpal.research.kex.trace.runner

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.protocol.Client2MasterConnection
import org.vorpal.research.kex.trace.symbolic.protocol.ControllerProtocolHandler
import org.vorpal.research.kex.trace.symbolic.protocol.ControllerProtocolSocketHandler
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionTimedOutResult
import org.vorpal.research.kex.trace.symbolic.protocol.TestExecutionRequest
import org.vorpal.research.kex.util.getJavaPath
import org.vorpal.research.kex.util.getJvmModuleParams
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.kexHome
import org.vorpal.research.kex.util.outputDirectory
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.nullFile
import org.vorpal.research.kthelper.terminateOrKill
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

@ExperimentalSerializationApi
@InternalSerializationApi
internal object ExecutorMasterController : AutoCloseable {
    private lateinit var process: Process
    private lateinit var controllerSocket: ControllerProtocolHandler

    init {
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            if (::process.isInitialized && process.isAlive)
                process.terminateOrKill(attempts = 10U, waitTime = 500.milliseconds)
        })
    }

    fun start(ctx: ExecutionContext) {
        controllerSocket = ControllerProtocolSocketHandler(ctx)
        val outputDir = kexConfig.outputDirectory
        val executorPath = kexConfig.getPathValue("executor", "executorPath") {
            kexConfig.kexHome.resolve("kex-executor/target/kex-executor-0.0.8-jar-with-dependencies.jar")
        }.toAbsolutePath()
        val executorKlass = "org.vorpal.research.kex.launcher.MasterLauncherKt"
        val executorConfigPath = kexConfig.getPathValue("executor", "executorConfigPath") {
            kexConfig.kexHome.resolve("kex.ini")
        }.toAbsolutePath()
        val executorPolicyPath = kexConfig.getPathValue("executor", "executorPolicyPath") {
            kexConfig.kexHome.resolve("kex.policy")
        }.toAbsolutePath()
        val masterJvmParams = kexConfig.getMultipleStringValue("executor", "masterJvmParams", ",").toTypedArray()
        val numberOfWorkers = kexConfig.getIntValue("executor", "numberOfWorkers", 1)
        val workerClassPath = listOfNotNull(
            executorPath,
            // this is really stupid, but currently required
            // because EasyRandom can generate some objects that are only available in kex-runner classpath
            *System.getProperty("java.class.path").split(getPathSeparator()).mapToArray { Paths.get(it) }
        )

        val kfgClassPath = ctx.classPath
        process = buildProcess(
            getJavaPath().toString(),
            "-Djava.security.manager", "-Djava.security.policy==${executorPolicyPath}",
            "-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener",
            *getJvmModuleParams().toTypedArray(),
            *masterJvmParams,
            "-classpath", executorPath.toString(),
            executorKlass,
            "--output", "${outputDir.toAbsolutePath()}",
            "--config", "$executorConfigPath",
            "--port", "${controllerSocket.controllerPort}",
            "--kfgClassPath", kfgClassPath.joinToString(getPathSeparator()),
            "--workerClassPath", workerClassPath.joinToString(getPathSeparator()),
            "--numOfWorkers", "$numberOfWorkers"
        ) {
            redirectOutput(nullFile())
            redirectError(nullFile())
            log.debug("Starting executor master process with command: '${command().joinToString(" ")}'")
        }
        runBlocking {
            controllerSocket.init()
        }
    }

    suspend fun getClientConnection(): Client2MasterConnection? {
        return controllerSocket.getClient2MasterConnection()
    }

    override fun close() {
        log.debug("Terminating executor controller process")
        process.terminateOrKill(attempts = 10U, waitTime = 500.milliseconds)
        log.debug("Executor controller terminated: ${process.isAlive}")
        controllerSocket.close()
    }
}

class SymbolicExternalTracingRunner(val ctx: ExecutionContext) {
    @ExperimentalSerializationApi
    @InternalSerializationApi
    suspend fun run(klass: String, setup: String, test: String): ExecutionResult {
        log.debug("Executing test $klass")

        val connection = ExecutorMasterController.getClientConnection()
        if (connection == null) {
            log.debug("Test $klass executed with result connection timeout")
            return ExecutionTimedOutResult("Connection timeout")
        }
        connection.use {
            if (!it.send(TestExecutionRequest(klass, test, setup))) {
                log.debug("Test $klass executed with result connection timeout")
                return ExecutionTimedOutResult("Connection timeout")
            }
            val result = it.receive()
            when (result) {
                null -> log.debug("Connection timeout")
                is ExecutionCompletedResult -> log.debug("Execution result: {}", result.symbolicState)
                else -> log.debug("Execution result: {}", result)
            }
            //log.debug("Test {} executed with result {}", klass, result)
            return result ?: ExecutionTimedOutResult("Connection timeout")
        }
    }
}
