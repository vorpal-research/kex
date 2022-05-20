package org.vorpal.research.kex.launcher

import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.FileConfig
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.WorkerCmdConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.trace.symbolic.protocol.Worker2MasterSocketConnection
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.getRuntime
import org.vorpal.research.kex.worker.ExecutorWorker
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.logging.log
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    WorkerLauncher(args).main()
}

@OptIn(InternalSerializationApi::class)
class WorkerLauncher(args: Array<String>) {
    private val cmd = WorkerCmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")
    private val port = cmd.getCmdValue("port")!!.toInt()

    val ctx: ExecutionContext

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        val logName = kexConfig.getStringValue("kex", "log", "kex-executor-worker.log")
        kexConfig.initLog(logName)

        val classPaths = cmd.getCmdValue("classpath")!!
            .split(getPathSeparator())
            .map { Paths.get(it).toAbsolutePath() }
        val containerClassLoader = URLClassLoader(classPaths.map { it.toUri().toURL() }.toTypedArray())

        val containers = classPaths.map {
            it.asContainer() ?: run {
                log.error("Can't represent ${it.toAbsolutePath()} as class container")
                exitProcess(1)
            }
        }
        val classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false, verifyIR = false))
        classManager.initialize(*listOfNotNull(*containers.toTypedArray(), getRuntime(), getIntrinsics()).toTypedArray())

        ctx = ExecutionContext(
            classManager,
            Package.defaultPackage,
            containerClassLoader,
            EasyRandomDriver(),
            containers.map { it.path }
        )
    }

    fun main() {
        val worker = ExecutorWorker(ctx, Worker2MasterSocketConnection(KexSerializer(ctx.cm), port))
        worker.run()
    }
}