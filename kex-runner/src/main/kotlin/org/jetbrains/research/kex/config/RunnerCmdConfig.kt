package org.jetbrains.research.kex.config

import org.apache.commons.cli.Option

class RunnerCmdConfig(args: Array<String>) : AbstractCmdConfig(args) {
    override fun setupOptions() {
        super.setupOptions()

        val packageOpt = Option("t", "target", true, "target to analyze: package, class or method")
        packageOpt.isRequired = false
        options.addOption(packageOpt)

        val ps = Option(null, "ps", true, "file with predicate state to debug; used only in debug mode")
        ps.isRequired = false
        options.addOption(ps)

        val logName = Option(null, "log", true, "log file name (`kex.log` by default)")
        logName.isRequired = false
        options.addOption(logName)

        val targetDir = Option(null, "output", true, "target directory for instrumented bytecode output")
        targetDir.isRequired = false
        options.addOption(targetDir)

        val libPackage =
            Option(null, "libCheck", true, "package to check use cases of library, used in LibChecker mode")
        libPackage.isRequired = false
        options.addOption(libPackage)

        val attempts = Option(null, "attempts", true, "number of attempts for reanimator mode")
        attempts.isRequired = false
        options.addOption(attempts)

        val mode = Option("m", "mode", true, "run mode: symbolic, concolic or debug")
        mode.isRequired = false
        options.addOption(mode)
    }
}
