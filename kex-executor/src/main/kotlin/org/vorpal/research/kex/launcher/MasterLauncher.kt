package org.vorpal.research.kex.launcher
import org.vorpal.research.kex.config.FileConfig
import org.vorpal.research.kex.config.MasterCmdConfig
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.trace.symbolic.protocol.MasterProtocolSocketHandler
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.worker.ExecutorMaster
import java.nio.file.Paths

fun main(args: Array<String>) {
    MasterLauncher(args).main()
}

class MasterLauncher(args: Array<String>) {
    private val cmd = MasterCmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")
    private val port = cmd.getCmdValue("port")!!.toInt()
    private val kfgClassPath = cmd.getCmdValue("kfgClassPath")!!
        .split(getPathSeparator())
        .map { Paths.get(it).toAbsolutePath() }
    private val workerClassPath = cmd.getCmdValue("workerClassPath")!!
        .split(getPathSeparator())
        .map { Paths.get(it).toAbsolutePath() }
    private val numberOfWorkers = cmd.getCmdValue("numOfWorkers")?.toInt() ?: 1

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        val logName = kexConfig.getStringValue("kex", "log", "kex-executor-master.log")
        kexConfig.initLog(logName)
    }
    fun main() {
        val master = ExecutorMaster(
            MasterProtocolSocketHandler(port),
            kfgClassPath,
            workerClassPath,
            numberOfWorkers
        )

        master.run()
    }
}