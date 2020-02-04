package org.jetbrains.research.kex.config

import kotlinx.serialization.enumMembers
import org.apache.commons.cli.*
import org.jetbrains.research.kex.util.exit
import org.jetbrains.research.kex.util.log
import java.io.PrintWriter
import java.io.StringWriter
import org.apache.commons.cli.CommandLine as Cmd

class CmdConfig(args: Array<String>) : Config() {
    private val options = Options()
    private val commandLineOptions = hashMapOf<String, MutableMap<String, String>>()
    private val cmd: Cmd

    init {
        setupOptions()

        val parser = DefaultParser()

        cmd = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
            exit<Cmd> {
                System.err.println("Error parsing command line arguments: ${e.message}")
                printHelp()
            }
        }

        getCmdValue("help")?.let {
            exit {
                printHelp()
            }
        }

        val optValues = cmd.getOptionValues("option") ?: arrayOf()
        for (index in optValues.indices step 3) {
            try {
                val section = optValues[index]
                val name = optValues[index + 1]
                val value = optValues[index + 2]
                commandLineOptions.getOrPut(section, ::hashMapOf)[name] = value
            } catch (e: IndexOutOfBoundsException) {
                exit<Cmd> {
                    log.error("Not enough arguments for `option`")
                    printHelp()
                }
            }
        }
    }

    private fun setupOptions() {
        val helpOpt = Option("h", "help", false, "print this help and quit")
        helpOpt.isRequired = false
        options.addOption(helpOpt)

        val jarOpt = Option("j", "jar", true, "input jar file path")
        jarOpt.isRequired = true
        options.addOption(jarOpt)

        val mainOpt = Option("p", "package", true, "analyzed package")
        mainOpt.isRequired = false
        options.addOption(mainOpt)

        val propOpt = Option(null, "config", true, "configuration file")
        propOpt.isRequired = false
        options.addOption(propOpt)

        val ps = Option(null, "ps", true, "file with predicate state to debug; used only in debug mode")
        ps.isRequired = false
        options.addOption(ps)

        val logName = Option(null, "log", true, "log file name (`kex.log` by default)")
        logName.isRequired = false
        options.addOption(logName)

        val targetDir = Option(null, "target", true, "target directory for instrumented bytecode output")
        targetDir.isRequired = false
        options.addOption(targetDir)

        val config = Option.builder()
                .longOpt("option")
                .argName("section:name:value")
                .valueSeparator(':')
                .numberOfArgs(3)
                .desc("set kex option through command line")
                .required(false)
                .build()
        options.addOption(config)

        val mode = Option("m", "mode", true, "run mode: bmc, concolic or debug")
        mode.isRequired = false
        options.addOption(mode)
    }

    fun getCmdValue(name: String): String? = cmd.getOptionValue(name)
    fun getCmdValue(name: String, default: String) = getCmdValue(name) ?: default
    override fun getStringValue(section: String, name: String): String? = commandLineOptions[section]?.get(name)

    fun printHelp() {
        val helpFormatter = HelpFormatter()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        helpFormatter.printHelp(pw, 80, "kex", null, options, 1, 3, null)

        println("$sw")
    }

    inline fun <reified T : Enum<T>> getEnumValue(name: String): Enum<T>? {
        val constName = getCmdValue(name) ?: return null
        return T::class.enumMembers().firstOrNull { it.name == constName }
    }

    inline fun <reified T : Enum<T>> getEnumValue(name: String, default: T): Enum<T> =
            getEnumValue(name) ?: default
}