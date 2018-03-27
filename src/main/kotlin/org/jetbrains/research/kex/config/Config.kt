package org.jetbrains.research.kex.config

interface Config {

    fun getStringValue(param: String): String?

    fun getBooleanValue(param: String): Boolean? = getStringValue(param)?.toBoolean()
    fun getIntValue(param: String): Int? = getStringValue(param)?.toInt()
    fun getDoubleValue(param: String): Double? = getStringValue(param)?.toDouble()

    fun getBooleanValue(param: String, default: Boolean = false) = getBooleanValue(param) ?: default
    fun getStringValue(param: String, default: String = "") = getStringValue(param) ?: default
    fun getIntValue(param: String, default: Int = 0) = getStringValue(param)?.toInt() ?: default
    fun getDoubleValue(param: String, default: Double = 0.0) = getStringValue(param)?.toDouble() ?: default

    fun getMultipleStringValue(param: String, delimeter: String = ",") = getStringValue(param)?.split(delimeter)?.map { it.replace("\\s".toRegex(), "") }?.toList()
            ?: listOf()

    fun getMultipleIntValue(param: String, delimeter: String = ",") = getMultipleStringValue(param, delimeter).map { it.toInt() }

    fun getMultipleDoubleValue(param: String, delimeter: String = ",") = getMultipleStringValue(param, delimeter).map { it.toDouble() }
}