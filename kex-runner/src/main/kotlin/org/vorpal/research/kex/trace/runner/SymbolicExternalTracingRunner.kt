package org.vorpal.research.kex.trace.runner

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.trace.symbolic.ExecutionResult
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.logging.log
import java.io.InputStreamReader
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

private val timeout = kexConfig.getLongValue("runner", "timeout", 10000L)

class ExecutionException(cause: Throwable) : KtException("", cause)

class SymbolicExternalTracingRunner(val ctx: ExecutionContext) {
    private val outputDir = kexConfig.getPathValue("kex", "outputDir")!!
    private val traceFile = outputDir.resolve("trace.json").toAbsolutePath()
    private val executorPolicyPath = (kexConfig.getPathValue(
        "executor", "executorPolicyPath"
    ) ?: Paths.get("kex.policy")).toAbsolutePath()
    private val executorPath = (kexConfig.getPathValue(
        "executor", "executorPath"
    ) ?: Paths.get("kex-executor/target/kex-executor-0.0.1-jar-with-dependencies.jar")).toAbsolutePath()
    private val executorKlass = "org.vorpal.research.kex.KexExecutorKt"
    private val executorConfigPath = (kexConfig.getPathValue(
        "executor", "executorConfigPath"
    ) ?: Paths.get("kex.ini")).toAbsolutePath()
    private val instrumentedCodeDir = outputDir.resolve(
        kexConfig.getStringValue("output", "instrumentedDir", "instrumented")
    ).toAbsolutePath()
    private val compiledCodeDir = outputDir.resolve(
        kexConfig.getStringValue("compile", "compileDir", "compiled")
    ).toAbsolutePath()
    private val executionClassPath = listOfNotNull(
        executorPath,
        instrumentedCodeDir,
        compiledCodeDir,
        getIntrinsics()?.path
    )

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun run(klass: String, setup: String, test: String): ExecutionResult {
        val pb = ProcessBuilder(
            "java",
            "-Djava.security.manager",
            "-Djava.security.policy==${executorPolicyPath}",
            "-classpath", executionClassPath.joinToString(getPathSeparator()),
            executorKlass,
            "--config", executorConfigPath.toString(),
            "--option", "kex:log:${outputDir.resolve("kex-executor.log").toAbsolutePath()}",
            "--classpath", ctx.classPath.joinToString(getPathSeparator()),
            "--package", ctx.pkg.toString().replace('/', '.'),
            "--class", klass,
            "--setup", setup,
            "--test", test,
            "--output", "$traceFile"
        )
        log.debug("Executing process with command:\n${pb.command().joinToString(" ")}")

        try {
            val process = pb.start()
            process.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (process.isAlive) {
                process.destroy()
            }

            val errorStream = InputStreamReader(process.errorStream).readText()
            if (errorStream.isNotBlank()) {
                log.debug("Process error stream: $errorStream")
            }

            val result = KexSerializer(ctx.cm).fromJson<ExecutionResult>(traceFile.readText()).also {
                traceFile.deleteIfExists()
            }
            log.debug("Execution result: $result")
            return result
        } catch (e: InterruptedException) {
            throw ExecutionException(e)
        }
    }
}