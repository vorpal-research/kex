package org.jetbrains.research.kex.config

import java.util.regex.Pattern

abstract class Config {
    abstract fun getStringValue(section: String, name: String): String?

    open fun getBooleanValue(section: String, name: String): Boolean? = getStringValue(section, name)?.toBoolean()
    open fun getIntValue(section: String, name: String): Int? = getStringValue(section, name)?.toInt()
    open fun getLongValue(section: String, name: String): Long? = getStringValue(section, name)?.toLong()
    open fun getDoubleValue(section: String, name: String): Double? = getStringValue(section, name)?.toDouble()

    open fun getBooleanValue(section: String, name: String, default: Boolean = false) =
            getBooleanValue(section, name) ?: default

    open fun getStringValue(section: String, name: String, default: String = "") =
            getStringValue(section, name) ?: default

    open fun getIntValue(section: String, name: String, default: Int = 0) =
            getIntValue(section, name) ?: default

    open fun getLongValue(section: String, name: String, default: Long = 0L) =
            getLongValue(section, name) ?: default

    open fun getDoubleValue(section: String, name: String, default: Double = 0.0) =
            getStringValue(section, name)?.toDouble() ?: default

    open fun getMultipleStringValue(section: String, name: String, delimiter: String = ",") =
            getStringValue(section, name)
                    ?.split(delimiter)
                    ?.asSequence()
                    ?.map { it.replace("\\s".toRegex(), "") }
                    ?.toList()
                    ?: listOf()

    open fun getMultipleIntValue(section: String, name: String, delimeter: String = ",") =
            getMultipleStringValue(section, name, delimeter).map { it.toInt() }

    open fun getMultipleLongValue(section: String, name: String, delimeter: String = ",") =
            getMultipleStringValue(section, name, delimeter).map { it.toLong() }

    open fun getMultipleDoubleValue(section: String, name: String, delimeter: String = ",") =
            getMultipleStringValue(section, name, delimeter).map { it.toDouble() }

    inline fun <reified T : Enum<T>> getEnumValue(section: String, name: String, ignoreCase: Boolean = false): T? {
        val constName = getStringValue(section, name) ?: return null
        val comparator = when {
            ignoreCase -> { a: String, b: String ->
                val pattern = Pattern.compile(a, Pattern.CASE_INSENSITIVE)
                pattern.matcher(b).find()
            }
            else -> { a: String, b: String -> a == b }
        }
        return T::class.java.enumConstants.firstOrNull { comparator(it.name, constName) }
    }

    inline fun <reified T : Enum<T>> getEnumValue(section: String, name: String, ignoreCase: Boolean = false, default: T): T =
            getEnumValue<T>(section, name, ignoreCase) ?: default
}