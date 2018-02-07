package org.jetbrains.research.kex.config

import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import org.apache.commons.cli.CommandLine as Cmd

class CmdConfig(args: Array<String>) : Config {
    private val log = LoggerFactory.getLogger(CmdConfig::class.java)
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

        val mainOpt = Option("m", "main", true, "main class of project")
        mainOpt.isRequired = true
        options.addOption(mainOpt)

        val propOpt = Option("p", "properties", true, "custom properties file")
        propOpt.isRequired = false
        options.addOption(propOpt)
    }

    override fun getStringValue(param: String): String? = cmd?.getOptionValue(param)

    fun printHelp() {
        val helpFormatter = HelpFormatter()
        helpFormatter.printHelp("kex", options)
    }
}