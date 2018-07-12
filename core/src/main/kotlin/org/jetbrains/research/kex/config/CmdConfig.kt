package org.jetbrains.research.kex.config

import org.apache.commons.cli.*
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.unreachable
import java.io.PrintWriter
import java.io.StringWriter
import org.apache.commons.cli.CommandLine as Cmd

class CmdConfig(args: Array<String>) : Config, Loggable {
    private val options = Options()
    private val cmd: Cmd

    init {
        setupOptions()

        val parser = DefaultParser()

        cmd = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
            unreachable {
                log.error("Error parsing command line arguments: ${e.message}")
                printHelp()
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

        val propOpt = Option(null, "properties", true, "custom properties file")
        propOpt.isRequired = false
        options.addOption(propOpt)

        val randomOpt = Option(null, "use-random-generator", false, "use random object generation")
        randomOpt.isRequired = false
        options.addOption(randomOpt)
    }

    override fun getStringValue(param: String): String? = cmd.getOptionValue(param)

    fun printHelp() {
        val helpFormatter = HelpFormatter()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        helpFormatter.printHelp(pw, 80, "kex", null, options, 1, 3, null)

        log.debug("$sw")
    }
}