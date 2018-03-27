package org.jetbrains.research.kex.config

object GlobalConfig : Config {
    private val sources = mutableListOf<Config>()

    override fun getStringValue(param: String): String? {
        var ret: String? = null
        for (src in sources) {
            ret = src.getStringValue(param)
            if (ret != null) break
        }
        return ret
    }

    fun initialize(sources: List<Config>) {
        this.sources.clear()
        this.sources.addAll(sources)
    }
}