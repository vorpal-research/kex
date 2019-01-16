package org.jetbrains.research.kex.config

import org.ini4j.Wini
import java.io.FileReader

class FileConfig(configFile: String) : Config {
    private val ini = Wini()

    init {
        val conf = org.ini4j.Config()
        conf.isMultiOption = true
        conf.isMultiSection = true
        ini.config = conf
        ini.load(FileReader(configFile))
    }

    override fun getStringValue(section: String, name: String): String? = ini[section]?.get(name)
    override fun getMultipleStringValue(section: String, name: String, delimiter: String): List<String> {
        return ini[section]?.getAll(name) ?: listOf()
    }
}