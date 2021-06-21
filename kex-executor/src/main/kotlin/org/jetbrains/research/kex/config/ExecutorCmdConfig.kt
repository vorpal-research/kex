package org.jetbrains.research.kex.config

import org.apache.commons.cli.Option

class ExecutorCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-executor", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val outputFile = Option(null, "output", true, "output file for serialized trace")
    outputFile.isRequired = false
    options += outputFile

    val logName = Option(null, "log", true, "log file name (`kex-executor.log` by default)")
    logName.isRequired = false
    options += logName

    options
})