package org.jetbrains.research.kex.config

import org.ini4j.Ini
import java.io.FileReader

class FileConfig(configFile: String) : Config {
    private val ini = Ini(FileReader(configFile))

    override fun getStringValue(section: String, name: String): String? = ini.get(section)?.getValue(name)
}