package org.jetbrains.research.kex.config

val kexConfig: GlobalConfig by lazy { GlobalConfig() }

class GlobalConfig : Config() {
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

    fun initLog(filename: String) {
        val outputDir = kexConfig.getPathValue("kex", "output-dir")!!
        System.setProperty("kex.log.name", outputDir.resolve(filename).toString())
    }

    fun initialize(sources: List<Config>) {
        this.sources.clear()
        this.sources.addAll(sources)
    }

    fun initialize(vararg sources: Config) = initialize(sources.toList())
}