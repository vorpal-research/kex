@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug


class BasicTests {

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

}