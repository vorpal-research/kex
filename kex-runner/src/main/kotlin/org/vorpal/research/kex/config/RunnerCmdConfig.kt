package org.vorpal.research.kex.config

import org.apache.commons.cli.Option
import org.vorpal.research.kex.launcher.LaunchMode

class RunnerCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-runner", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val packageOpt = Option("t", "target", true, "target to analyze: package, class or method")
    packageOpt.isRequired = false
    options += packageOpt

    val targetDir = Option(null, "output", true, "directory for all temporary output")
    targetDir.isRequired = false
    options += targetDir

    val mode = Option("m", "mode", true, "run mode: ${
        LaunchMode.values().joinToString(", ") { it.toString().lowercase() }
    }")
    mode.isRequired = false
    options += mode
    options
})

