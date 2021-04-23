package org.jetbrains.research.kex.asm.analysis.defect

import com.beust.klaxon.Klaxon
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import java.io.File

private val klaxon = Klaxon()

object DefectManager {
    private val innerDefects = mutableSetOf<Defect>()
    val defectFile by lazy {
        kexConfig.getStringValue("defect", "outputFile")
            ?: unreachable { log.error("You need to specify parameters file to be able to use Z3 SMT") }
    }
    val defects: Set<Defect> get() = innerDefects

    operator fun plusAssign(defect: Defect) {
        innerDefects += defect
    }

    fun emit() {
        val json = klaxon.toJsonString(defects)
        val file = File(defectFile).also {
            it.parentFile?.mkdirs()
        }
        file.writeText(json)
    }
}