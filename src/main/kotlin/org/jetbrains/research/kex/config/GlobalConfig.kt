package org.jetbrains.research.kex.config

class GlobalConfig private constructor() : Config {
    private val sources = mutableListOf<Config>()

    override fun getStringValue(param: String): String? {
        var ret: String? = null
        for (src in sources) {
            ret = src.getStringValue(param)
            if (ret != null) break
        }
        return ret
    }

    private object Holder { val instance = GlobalConfig() }

    companion object {
        val instance: GlobalConfig by lazy { Holder.instance }

        fun initialize(sources: List<Config>) {
            instance.sources.clear()
            instance.sources.addAll(sources)
        }
    }
}