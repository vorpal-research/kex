package org.vorpal.research.kex.config

import org.apache.commons.cli.Option

class ExecutorCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-executor", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val targetDir = Option(null, "output", true, "directory for all temporary output")
    targetDir.isRequired = false
    options += targetDir

    val pkg = Option(null, "package", true, "pkg")
    pkg.isRequired = true
    options += pkg

    val outputFile = Option(null, "output", true, "output file for serialized trace")
    outputFile.isRequired = false
    options += outputFile

    val className = Option(null, "class", true, "klass to run")
    className.isRequired = true
    options += className

    val setupMethodName = Option(null, "setup", true, "setup method name")
    setupMethodName.isRequired = true
    options += setupMethodName

    val testMethodName = Option(null, "test", true, "test method name")
    testMethodName.isRequired = true
    options += testMethodName

    options
})