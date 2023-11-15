package org.vorpal.research.kex.config

import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

abstract class Config {
    abstract fun getStringValue(section: String, name: String): String?

    open fun getBooleanValue(section: String, name: String): Boolean? = getStringValue(section, name)?.toBoolean()
    open fun getIntValue(section: String, name: String): Int? = getStringValue(section, name)?.toInt()
    open fun getLongValue(section: String, name: String): Long? = getStringValue(section, name)?.toLong()
    open fun getDoubleValue(section: String, name: String): Double? = getStringValue(section, name)?.toDouble()

    open fun getBooleanValue(section: String, name: String, default: Boolean = false) =
            getBooleanValue(section, name) ?: default

    open fun getBooleanValue(section: String, name: String, defaultProvider: () -> Boolean) =
        getBooleanValue(section, name) ?: defaultProvider()

    open fun getStringValue(section: String, name: String, default: String = "") =
            getStringValue(section, name) ?: default

    open fun getStringValue(section: String, name: String, defaultProvider: () -> String) =
        getStringValue(section, name) ?: defaultProvider()

    open fun getIntValue(section: String, name: String, default: Int = 0) =
            getIntValue(section, name) ?: default

    open fun getIntValue(section: String, name: String, defaultProvider: () -> Int) =
        getIntValue(section, name) ?: defaultProvider()

    open fun getLongValue(section: String, name: String, default: Long = 0L) =
            getLongValue(section, name) ?: default

    open fun getLongValue(section: String, name: String, defaultProvider: () -> Long) =
        getLongValue(section, name) ?: defaultProvider()

    open fun getDoubleValue(section: String, name: String, default: Double = 0.0) =
            getStringValue(section, name)?.toDouble() ?: default

    open fun getDoubleValue(section: String, name: String, defaultProvider: () -> Double) =
        getStringValue(section, name)?.toDouble() ?: defaultProvider()

    open fun getMultipleStringValue(section: String, name: String, delimiter: String = ",") =
            getStringValue(section, name)
                    ?.split(delimiter)
                    ?.map { it.replace("\\s".toRegex(), "") }
                    ?: listOf()

    open fun getMultipleIntValue(section: String, name: String, delimiter: String = ",") =
            getMultipleStringValue(section, name, delimiter).map { it.toInt() }

    open fun getMultipleLongValue(section: String, name: String, delimiter: String = ",") =
            getMultipleStringValue(section, name, delimiter).map { it.toLong() }

    open fun getMultipleDoubleValue(section: String, name: String, delimiter: String = ",") =
            getMultipleStringValue(section, name, delimiter).map { it.toDouble() }

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

    open fun getPathValue(section: String, name: String): Path? = getStringValue(section, name)?.let { Paths.get(it) }
    open fun getPathValue(section: String, name: String, default: String): Path = getStringValue(section, name, default).let { Paths.get(it) }
    open fun getPathValue(section: String, name: String, default: Path): Path = getStringValue(section, name)?.let { Paths.get(it) } ?: default
    open fun getPathValue(section: String, name: String, default: () -> Path): Path = getStringValue(section, name)?.let { Paths.get(it) } ?: default()
}
