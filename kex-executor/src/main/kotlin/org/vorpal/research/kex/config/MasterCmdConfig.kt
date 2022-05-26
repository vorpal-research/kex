package org.vorpal.research.kex.config

import org.apache.commons.cli.Option

class MasterCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-executor-master", args, {
    val options = mutableListOf<Option>()

    val targetDir = Option(null, "output", true, "directory for all temporary output")
    targetDir.isRequired = false
    options += targetDir

    val kfgClassPath = Option(null, "kfgClassPath", true, "classpath for kfg initialization")
    kfgClassPath.isRequired = true
    options += kfgClassPath

    val workerClassPath = Option(null, "workerClassPath", true, "classpath for starting JVM with worker process")
    workerClassPath.isRequired = true
    options += workerClassPath

    val port = Option("p", "port", true, "port on which to start listening for clients")
    port.isRequired = true
    options += port

    val workers = Option("n", "numOfWorkers", true, "number of workers to start")
    workers.isRequired = false
    options += workers

    options += Option(null, "config", true, "configuration file").also { it.isRequired = false }

    options
})
