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

    override fun getMultipleStringValue(section: String, name: String, delimiter: String): List<String> {
        return sources.flatMap { it.getMultipleStringValue(section, name, delimiter) }
    }

    fun initialize(sources: List<Config>) {
        this.sources.clear()
        this.sources.addAll(sources)
    }

    fun initialize(vararg sources: Config) = initialize(sources.toList())
}