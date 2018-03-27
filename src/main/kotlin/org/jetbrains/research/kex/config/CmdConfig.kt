package org.jetbrains.research.kex.config

import org.apache.commons.cli.*
import org.jetbrains.research.kex.util.Loggable
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import org.apache.commons.cli.CommandLine as Cmd

class CmdConfig(args: Array<String>) : Config, Loggable {
    private val options = Options()
    private var cmd: Cmd? = null

    init {
        setupOptions()

        val parser = DefaultParser()

        try {
            cmd = parser.parse(options, args)
        } catch (e: ParseException) {
            log.error("Error parsing command line arguments: ${e.message}")
            printHelp()
            System.exit(1)
        }
    }

    private fun setupOptions() {
        val jarOpt = Option("j", "jar", true, "input jar file path")
        jarOpt.isRequired = true
        options.addOption(jarOpt)

        val mainOpt = Option("p", "package", true, "analyzed package")
        mainOpt.isRequired = true
        options.addOption(mainOpt)

        val propOpt = Option(null, "properties", true, "custom properties file")
        propOpt.isRequired = false
        options.addOption(propOpt)
    }

    override fun getStringValue(param: String): String? = cmd?.getOptionValue(param)

    fun printHelp() {
        val helpFormatter = HelpFormatter()
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        helpFormatter.printHelp(pw, 80, "kex", null, options, 1, 3, null)

        log.debug("$sw")
    }
}