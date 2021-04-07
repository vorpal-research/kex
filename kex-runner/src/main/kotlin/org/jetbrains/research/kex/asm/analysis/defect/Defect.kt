package org.jetbrains.research.kex.asm.analysis.defect

import org.jetbrains.research.kfg.ir.Location
import java.nio.file.Path

enum class DefectType(val description: String) {
    OOB("array index out of bounds"),
    NPE("null pointer exception"),
    ASSERT("assertion violation")
}

data class Defect(
    val location: Location,
    val type: DefectType,
    val id: String?,
    val testFile: Path?,
    val testCaseName: String?
) {
    companion object {
        fun oob(location: Location, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(location, DefectType.OOB, id, testFile, testCaseName)

        fun npe(location: Location, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(location, DefectType.NPE, id, testFile, testCaseName)

        fun assert(location: Location, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(location, DefectType.ASSERT, id, testFile, testCaseName)
    }
}