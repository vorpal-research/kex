package org.jetbrains.research.kex.asm.analysis.defect

import kotlinx.serialization.Serializable
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import java.nio.file.Path
import kotlin.io.path.absolutePathString

enum class DefectType(val description: String) {
    OOB("array index out of bounds"),
    NPE("null pointer exception"),
    ASSERT("assertion violation")
}

@Serializable
data class Defect(
    val type: DefectType,
    val callStack: List<String>,
    val id: String?,
    val testFile: String?,
    val testCaseName: String?
) {
    companion object {
        fun oob(callStack: List<String>, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(DefectType.OOB, callStack, id, testFile?.absolutePathString(), testCaseName)

        fun npe(callStack: List<String>, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(DefectType.NPE, callStack, id, testFile?.absolutePathString(), testCaseName)

        fun assert(callStack: List<String>, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(DefectType.ASSERT, callStack, id, testFile?.absolutePathString(), testCaseName)


        fun oob(inst: Instruction, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            oob(listOf("${inst.parent.parent} - ${inst.location}"), id, testFile, testCaseName)

        fun npe(inst: Instruction, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            npe(listOf("${inst.parent.parent} - ${inst.location}"), id, testFile, testCaseName)

        fun assert(inst: Instruction, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            assert(listOf("${inst.parent.parent} - ${inst.location}"), id, testFile, testCaseName)
    }
}