package org.jetbrains.research.kex.config

import org.apache.commons.cli.*
import org.jetbrains.research.kex.util.exit
import org.jetbrains.research.kex.util.log
import java.io.PrintWriter
import java.io.StringWriter
import org.apache.commons.cli.CommandLine as Cmd

class CmdConfig(args: Array<String>) : Config {
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
                log.error("Error parsing command line arguments: ${e.message}")
                printHelp()
            }
        }
        val optValues = cmd.getOptionValues("option") ?: arrayOf()
        for (index in 0 until optValues.size step 3) {
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

        val config = Option.builder()
                .longOpt("option")
                .argName("section:name:value")
                .valueSeparator(':')
                .numberOfArgs(3)
                .desc("set kex option through command line")
                .required(false)
                .build()
        options.addOption(config)
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
}