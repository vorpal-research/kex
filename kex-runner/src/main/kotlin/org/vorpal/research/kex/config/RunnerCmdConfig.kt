package org.vorpal.research.kex.config

import org.apache.commons.cli.Option
import org.vorpal.research.kex.launcher.LaunchMode

class RunnerCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-runner", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val targetOpt = Option(
        "t",
        "target",
        true,
        "target to analyze: package, class or method; required for symbolic, concolic and defect modes"
    )
    targetOpt.isRequired = false
    options += targetOpt

    val libraryTargetOpt = Option(
        null,
        "libraryTarget",
        true,
        "package where to analyze library usages, required for the libchecker mode"
    )
    libraryTargetOpt.isRequired = false
    options += libraryTargetOpt

    val traceOpt = Option(
        null,
        "trace",
        true,
        "path to a trace file with a crash, required for the crash mode"
    )
    traceOpt.isRequired = false
    options += traceOpt

    val depthOpt = Option(
        null,
        "depth",
        true,
        "depth with which to analyze the crash, required for the crash mode"
    )
    depthOpt.isRequired = false
    options += depthOpt

    val outputDirOpt = Option(
        null,
        "output",
        true,
        "directory for all temporary output"
    )
    outputDirOpt.isRequired = false
    options += outputDirOpt

    val mode = Option("m", "mode", true, "run mode: ${
        LaunchMode.entries.joinToString(", ") { it.toString().lowercase() }
    }")
    mode.isRequired = false
    options += mode

    options
})

