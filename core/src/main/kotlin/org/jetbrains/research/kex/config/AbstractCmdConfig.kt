package org.jetbrains.research.kex.config

import org.apache.commons.cli.*
import org.jetbrains.research.kthelper.assert.exit
import org.jetbrains.research.kthelper.logging.log
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern

abstract class AbstractCmdConfig(args: Array<String>) : Config() {
    protected val options = Options()
    protected val commandLineOptions = hashMapOf<String, MutableMap<String, String>>()
    protected val cmd: CommandLine

    init {
        setupOptions()

        val parser = DefaultParser()

        cmd = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
            exit<CommandLine> {
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
                exit<CommandLine> {
                    log.error("Not enough arguments for `option`")
                    printHelp()
                }
            }
        }
    }

    protected open fun setupOptions() {
        val helpOpt = Option("h", "help", false, "print this help and quit")
        helpOpt.isRequired = false
        options.addOption(helpOpt)

        val jarOpt = Option.builder("cp")
            .longOpt("classpath")
            .hasArg(true)
            .argName("arg[:arg]")
            .desc("classpath for analysis, jar files and directories separated by `:`")
            .required(true)
            .build()
        options.addOption(jarOpt)

        val propOpt = Option(null, "config", true, "configuration file")
        propOpt.isRequired = false
        options.addOption(propOpt)

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
        println(helpString)
    }

    val helpString: String
        get() {
            val helpFormatter = HelpFormatter()
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            helpFormatter.printHelp(pw, 80, "kex", null, options, 1, 3, null)
            return sw.toString()
        }

    inline fun <reified T : Enum<T>> getEnumValue(name: String, ignoreCase: Boolean = false): Enum<T>? {
        val constName = getCmdValue(name) ?: return null
        val comparator = when {
            ignoreCase -> { a: String, b: String ->
                val pattern = Pattern.compile(a, Pattern.CASE_INSENSITIVE)
                pattern.matcher(b).matches()
            }
            else -> { a: String, b: String -> a == b }
        }
        return T::class.java.enumConstants.firstOrNull { comparator(it.name, constName) }
    }

    inline fun <reified T : Enum<T>> getEnumValue(name: String, default: T, ignoreCase: Boolean = false): Enum<T> =
        getEnumValue(name, ignoreCase) ?: default
}
