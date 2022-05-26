package org.vorpal.research.kex.config

import org.apache.commons.cli.Option

class WorkerCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-executor-worker", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val targetDir = Option(null, "output", true, "directory for all temporary output")
    targetDir.isRequired = false
    options += targetDir

    val port = Option("p", "port", true, "port on which to start listening for clients")
    port.isRequired = true
    options += port

    options
})