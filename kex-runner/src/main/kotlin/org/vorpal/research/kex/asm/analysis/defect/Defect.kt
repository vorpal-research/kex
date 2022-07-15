package org.vorpal.research.kex.asm.analysis.defect

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import java.nio.file.Path
import java.nio.file.Paths

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
    @Serializable(with = PathAsStringSerializer::class)
    val testFile: Path?,
    val testCaseName: String?
) {
    companion object {
        fun oob(callStack: List<String>, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(DefectType.OOB, callStack, id, testFile, testCaseName)

        fun npe(callStack: List<String>, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(DefectType.NPE, callStack, id, testFile, testCaseName)

        fun assert(callStack: List<String>, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            Defect(DefectType.ASSERT, callStack, id, testFile, testCaseName)


        fun oob(inst: Instruction, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            oob(listOf("${inst.parent.parent} - ${inst.location}"), id, testFile, testCaseName)

        fun npe(inst: Instruction, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            npe(listOf("${inst.parent.parent} - ${inst.location}"), id, testFile, testCaseName)

        fun assert(inst: Instruction, id: String? = null, testFile: Path? = null, testCaseName: String? = null) =
            assert(listOf("${inst.parent.parent} - ${inst.location}"), id, testFile, testCaseName)
    }
}

private object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Path = Paths.get(decoder.decodeString())
}