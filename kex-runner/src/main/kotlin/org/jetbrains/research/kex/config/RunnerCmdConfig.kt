package org.jetbrains.research.kex.config

import org.apache.commons.cli.Option

class RunnerCmdConfig(args: Array<String>) : AbstractCmdConfig("kex-runner", args, {
    val options = mutableListOf<Option>()
    options.addAll(AbstractCmdConfig.defaultOptions())

    val packageOpt = Option("t", "target", true, "target to analyze: package, class or method")
    packageOpt.isRequired = false
    options += packageOpt

    val ps = Option(null, "ps", true, "file with predicate state to debug; used only in debug mode")
    ps.isRequired = false
    options += ps

    val targetDir = Option(null, "output", true, "directory for all temporary output")
    targetDir.isRequired = false
    options += targetDir

    val libPackage =
        Option(null, "libCheck", true, "package to check use cases of library, used in LibChecker mode")
    libPackage.isRequired = false
    options += libPackage

    val attempts = Option(null, "attempts", true, "number of attempts for reanimator mode")
    attempts.isRequired = false
    options += attempts

    val mode = Option("m", "mode", true, "run mode: symbolic, concolic or debug")
    mode.isRequired = false
    options += mode
    options
})

