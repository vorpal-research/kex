@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class InspectionPositionalArguments(
        val name: String,
        val description: String,
        val minCount: Int?,
        val maxCount: Int?
)

enum class InspectionArgumentType {
    Parameter,
    MultiParameter,
    Flag
}

class InspectionArgument(
        val name: String,
        val aliasNames: List<String>,
        val shortNames: List<Char>,
        val description: String,
        val type: InspectionArgumentType,
        val isOptional: Boolean,
        val defaultValue: String? = null
)

class InspectionArguments(
        val arguments: List<InspectionArgument>,
        val name: String = "",
        val description: String = "",
        val positionalArguments: InspectionPositionalArguments?
) {
    fun generateHelpText(): String {
        var helpString = ""
        val tab = "  "

        if (!this.name.isEmpty()) {
            helpString += "${this.name}\n"
        }

        if (!this.description.isEmpty())
            helpString += "${this.description}\n\n"

        this.arguments.forEach { argument ->
            helpString += tab

            if (argument.isOptional)
                helpString += "["

            helpString += "--" + argument.name

            argument.aliasNames.forEach { aliasName ->
                helpString += " | --" + aliasName
            }

            argument.shortNames.forEach { shortName ->
                helpString += " | -" + shortName
            }

            if (argument.isOptional)
                helpString += "]"

            if (argument.type == InspectionArgumentType.MultiParameter) {
                helpString += " <value> (repeat parameter for multiple values)"
            } else if (argument.type == InspectionArgumentType.Parameter) {
                helpString += " <value>"
            }

            helpString += "\n"

            if (!argument.description.isEmpty()) {
                helpString += "$tab$tab${argument.description}\n"
            }
        }

        val positionalArgumentsConfig = this.positionalArguments

        if (positionalArgumentsConfig != null) {
            helpString += "\n"
            helpString += "$tab<${positionalArgumentsConfig.name}>..."

            if (positionalArgumentsConfig.minCount != null) {
                helpString += " (min ${positionalArgumentsConfig.minCount})"
            }

            if (positionalArgumentsConfig.maxCount != null) {
                helpString += " (max ${positionalArgumentsConfig.maxCount})"
            }

            helpString += "\n"

            if (!positionalArgumentsConfig.description.isEmpty()) {
                helpString += "$tab$tab${positionalArgumentsConfig.description}\n"
            }
        }

        return helpString
    }
}
