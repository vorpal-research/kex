package org.jetbrains.research.kex.config

object GlobalConfig : Config {
    private val sources = mutableListOf<Config>()

    override fun getStringValue(section: String, name: String): String? {
        var ret: String? = null
        for (src in sources) {
            ret = src.getStringValue(section, name)
            if (ret != null) break
        }
        return ret
    }

    fun initialize(sources: List<Config>) {
        GlobalConfig.sources.clear()
        GlobalConfig.sources.addAll(sources)
    }
}