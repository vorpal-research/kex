package org.vorpal.research.kex

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.config.FileConfig
import org.vorpal.research.kex.config.RunnerCmdConfig
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.launcher.ConcolicLauncher
import org.vorpal.research.kex.launcher.CrashReproductionLauncher
import org.vorpal.research.kex.launcher.DefectCheckerLauncher
import org.vorpal.research.kex.launcher.LaunchMode
import org.vorpal.research.kex.launcher.LauncherException
import org.vorpal.research.kex.launcher.LibraryCheckLauncher
import org.vorpal.research.kex.launcher.SymbolicLauncher
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
fun main(args: Array<String>) {
    val cmd = RunnerCmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))

    // initialize output dir
    val outputDirPath = cmd.getCmdValue("output")?.let { Paths.get(it) }
        ?: kexConfig.getPathValue("kex", "outputDir")
        ?: Files.createTempDirectory(Paths.get("."), "kex-output")
    RuntimeConfig.setValue("kex", "outputDir", outputDirPath.toAbsolutePath())

    val logName = kexConfig.getStringValue("kex", "log", "kex.log")
    kexConfig.initLog(logName)

    val classPaths = cmd.getCmdValue("classpath")?.split(getPathSeparator())
    require(classPaths != null, cmd::printHelp)

    try {
        val launcher = when (val mode = cmd.getEnumValue("mode", LaunchMode.Concolic, ignoreCase = true)) {
            LaunchMode.Crash -> {
                val traceFile = cmd.getCmdValue("trace")
                require(traceFile != null) {
                    log.error("Option 'trace' is required for the $mode mode")
                    cmd.printHelp()
                }

                val traceDepth = cmd.getCmdValue("depth")
                require(traceDepth?.toUIntOrNull() != null) {
                    log.error("Option 'depth' is required for the $mode mode")
                    cmd.printHelp()
                }

                CrashReproductionLauncher(classPaths, traceFile, traceDepth!!.toUInt())
            }

            else -> {
                val targetName = cmd.getCmdValue("target")
                require(targetName != null) {
                    log.error("Option 'target' is required for the $mode mode")
                    cmd.printHelp()
                }
                val minimizationFlag = cmd.getCmdValue("minimization", "false").toBoolean()

                when (mode) {
                    LaunchMode.LibChecker -> {
                        val libraryTarget = cmd.getCmdValue("libraryTarget")
                        require(libraryTarget != null) {
                            log.error("Option 'libraryTarget' is required for the $mode mode")
                            cmd.printHelp()
                        }

                        LibraryCheckLauncher(classPaths, targetName, libraryTarget)
                    }

                    LaunchMode.Symbolic -> {
                        SymbolicLauncher(classPaths, targetName, minimizationFlag)
                    }
                    LaunchMode.Concolic -> {
                        ConcolicLauncher(classPaths, targetName, minimizationFlag)
                    }
                    LaunchMode.DefectChecker -> {
                        DefectCheckerLauncher(classPaths, targetName)
                    }
                    else -> unreachable("")
                }
            }
        }
        launcher.launch()
    } catch (e: LauncherException) {
        log.error(e.message)
        exitProcess(1)
    }
}
