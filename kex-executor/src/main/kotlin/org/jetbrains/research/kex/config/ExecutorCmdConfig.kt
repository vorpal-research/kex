package org.jetbrains.research.kex.config

import org.apache.commons.cli.Option

class ExecutorCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-executor", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val outputFile = Option(null, "output", true, "output file for serialized trace")
    outputFile.isRequired = false
    options += outputFile

    val className = Option(null, "class", true, "klass to run")
    className.isRequired = true
    options += className

    val methodName = Option(null, "method", true, "method name")
    methodName.isRequired = true
    options += methodName

    options
})