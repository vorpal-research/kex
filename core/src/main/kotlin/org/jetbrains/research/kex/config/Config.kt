package org.jetbrains.research.kex.config

interface Config {
    fun getStringValue(section: String, name: String): String?

    fun getBooleanValue(section: String, name: String): Boolean? = getStringValue(section, name)?.toBoolean()
    fun getIntValue(section: String, name: String): Int? = getStringValue(section, name)?.toInt()
    fun getLongValue(section: String, name: String): Long? = getStringValue(section, name)?.toLong()
    fun getDoubleValue(section: String, name: String): Double? = getStringValue(section, name)?.toDouble()

    fun getBooleanValue(section: String, name: String, default: Boolean = false) =
            getBooleanValue(section, name) ?: default

    fun getStringValue(section: String, name: String, default: String = "") =
            getStringValue(section, name) ?: default

    fun getIntValue(section: String, name: String, default: Int = 0) =
            getIntValue(section, name) ?: default

    fun getLongValue(section: String, name: String, default: Long = 0L) =
            getLongValue(section, name) ?: default

    fun getDoubleValue(section: String, name: String, default: Double = 0.0) =
            getStringValue(section, name)?.toDouble() ?: default

    fun getMultipleStringValue(section: String, name: String, delimeter: String = ",") =
            getStringValue(section, name)
                    ?.split(delimeter)
                    ?.asSequence()
                    ?.map { it.replace("\\s".toRegex(), "") }
                    ?.toList()
                    ?: listOf()

    fun getMultipleIntValue(section: String, name: String, delimeter: String = ",") =
            getMultipleStringValue(section, name, delimeter).map { it.toInt() }

    fun getMultipleLongValue(section: String, name: String, delimeter: String = ",") =
            getMultipleStringValue(section, name, delimeter).map { it.toLong() }

    fun getMultipleDoubleValue(section: String, name: String, delimeter: String = ",") =
            getMultipleStringValue(section, name, delimeter).map { it.toDouble() }
}