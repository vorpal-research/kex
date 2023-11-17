package org.vorpal.research.kex.config

import java.nio.file.Path

object RuntimeConfig : Config() {
    private val options = mutableMapOf<String, MutableMap<String, String>>()

    fun setValue(section: String, name: String, value: String) =
            options.getOrPut(section) { mutableMapOf() }.set(name, value)

    fun setValue(section: String, name: String, value: Path) =
        options.getOrPut(section) { mutableMapOf() }.set(name, value.toString())

    fun setValue(section: String, name: String, value: Int) =
            options.getOrPut(section) { mutableMapOf() }.set(name, value.toString())

    fun setValue(section: String, name: String, value: Double) =
            options.getOrPut(section) { mutableMapOf() }.set(name, value.toString())

    fun setValue(section: String, name: String, value: Boolean) =
            options.getOrPut(section) { mutableMapOf() }.set(name, value.toString())

    override fun getStringValue(section: String, name: String) = options[section]?.get(name)
}