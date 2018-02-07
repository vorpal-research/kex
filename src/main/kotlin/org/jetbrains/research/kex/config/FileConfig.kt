package org.jetbrains.research.kex.config

import java.util.*

class FileConfig(configFile: String) : Config {
    private val properties = Properties()

    init {
        val input = javaClass.classLoader.getResourceAsStream(configFile)
        properties.load(input)
    }

    override fun getStringValue(param: String): String? = properties.getProperty(param)
}