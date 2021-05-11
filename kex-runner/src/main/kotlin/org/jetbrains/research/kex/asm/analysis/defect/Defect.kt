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
    val callStack: List<String>,
    val id: String?,
    val testFile: Path?,
    val testCaseName: String?
) {
    companion object {
        fun oob(location: Location, callStack: List<String> = listOf(),id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(location, DefectType.OOB, callStack, id, testFile, testCaseName)

        fun npe(location: Location, callStack: List<String> = listOf(),id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(location, DefectType.NPE, callStack, id, testFile, testCaseName)

        fun assert(location: Location, callStack: List<String> = listOf(),id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(location, DefectType.ASSERT, callStack, id, testFile, testCaseName)
    }
}