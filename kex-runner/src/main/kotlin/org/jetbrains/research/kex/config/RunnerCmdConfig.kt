package org.jetbrains.research.kex.config

import org.apache.commons.cli.Option
import org.jetbrains.research.kex.launcher.LaunchMode

class RunnerCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-runner", args, {
    val options = mutableListOf<Option>()
    options.addAll(defaultOptions())

    val packageOpt = Option("t", "target", true, "target to analyze: package, class or method")
    packageOpt.isRequired = false
    options += packageOpt

    val targetDir = Option(null, "output", true, "directory for all temporary output")
    targetDir.isRequired = false
    options += targetDir

    val libPackage =
        Option(null, "libCheck", true, "package to check use cases of library, used in LibChecker mode")
    libPackage.isRequired = false
    options += libPackage

    val lslPath =
        Option(null, "lslPath", true, "path to .lsl file")
    lslPath.isRequired = false
    options += lslPath

    val attempts = Option(null, "attempts", true, "number of attempts for reanimator mode")
    attempts.isRequired = false
    options += attempts

    val attempts = Option(null, "attempts", true, "number of attempts for reanimator mode")
    attempts.isRequired = false
    options += attempts

    val mode = Option("m", "mode", true, "run mode: ${
        LaunchMode.values().joinToString(", ") { it.toString().lowercase() }
    }")
    val libPackage =
        Option(null, "libCheck", true, "package to check use cases of library, used in LibChecker mode")
    libPackage.isRequired = false
    options += libPackage

    val lslPath =
        Option(null, "lslPath", true, "path to .lsl file")
    lslPath.isRequired = false
    options += lslPath

    mode.isRequired = false
    options += mode
    options
})

