package org.jetbrains.research.kex.asm.analysis.defect

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

object DefectManager {
    private val innerDefects = mutableSetOf<Defect>()
    private val outputDirectory by lazy { kexConfig.getPathValue("kex", "outputDir")!! }
    val defectFile by lazy {
        outputDirectory.resolve(kexConfig.getStringValue("defect", "outputFile")
            ?: unreachable { log.error("You need to specify parameters file to be able to use Z3 SMT") })
    }
    val defects: Set<Defect> get() = innerDefects

    operator fun plusAssign(defect: Defect) {
        innerDefects += defect
    }

    @ExperimentalSerializationApi
    fun emit() {
        val json = Json.encodeToString(defects)
        val file = defectFile.toFile().also {
            it.parentFile?.mkdirs()
        }
        file.writeText(json)
    }

    fun clear() {
        innerDefects.clear()
    }
}