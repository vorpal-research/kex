package org.vorpal.research.kex.config

import org.ini4j.Wini
import java.io.File
import org.ini4j.Config as IniConfig

class FileConfig(configFile: String) : Config() {
    private val ini = Wini()

    init {
        val conf = IniConfig()
        conf.isMultiOption = true
        conf.isMultiSection = true
        ini.config = conf
        ini.load(File(configFile).reader())
    }

    override fun getStringValue(section: String, name: String): String? = ini[section]?.get(name)
    override fun getMultipleStringValue(section: String, name: String, delimiter: String): List<String> {
        return ini[section]?.getAll(name) ?: listOf()
    }
}