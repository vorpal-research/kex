package org.vorpal.research.kex.jacoco

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A surrogate class used to simplify the code of the [SurrogateCoverageInfo]
 * by providing basic functionality of the decoding/encoding of the [CommonCoverageInfo]. The trick is found in
 * [kotlinx serialization guide](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#composite-serializer-via-surrogate).
 *
 *
 * The abstract class cannot be used in the first way to provide the serializer to abstract class
 */
@Serializable
private sealed class SurrogateCoverageInfo {
    abstract val name: String
    abstract val level: AnalysisUnit
    abstract val instructionCoverage: CoverageInfo
    abstract val branchCoverage: CoverageInfo
    abstract val linesCoverage: CoverageInfo
    abstract val complexityCoverage: CoverageInfo
}

@Serializable
@SerialName("method")
private data class SurrogateMethodCoverageInfo(
    override val name: String,
    override val level: AnalysisUnit,
    override val instructionCoverage: CoverageInfo,
    override val branchCoverage: CoverageInfo,
    override val linesCoverage: CoverageInfo,
    override val complexityCoverage: CoverageInfo
) : SurrogateCoverageInfo() {
    val value: MethodCoverageInfo
        get() = MethodCoverageInfo(name, instructionCoverage, branchCoverage, linesCoverage, complexityCoverage)

    companion object {
        fun fromValue(value: MethodCoverageInfo) = SurrogateMethodCoverageInfo(
            value.name,
            value.level,
            value.instructionCoverage,
            value.branchCoverage,
            value.linesCoverage,
            value.complexityCoverage,
        )
    }
}

@Serializable
@SerialName("class")
private data class SurrogateClassCoverageInfo(
    override val name: String,
    override val level: AnalysisUnit,
    override val instructionCoverage: CoverageInfo,
    override val branchCoverage: CoverageInfo,
    override val linesCoverage: CoverageInfo,
    override val complexityCoverage: CoverageInfo,
    val methods: List<SurrogateMethodCoverageInfo>
) : SurrogateCoverageInfo() {
    val value: ClassCoverageInfo
        get() {
            val result = ClassCoverageInfo(name, instructionCoverage, branchCoverage, linesCoverage, complexityCoverage)
            result.methods.addAll(methods.map { it.value })
            return result
        }

    companion object {
        fun fromValue(value: ClassCoverageInfo) = SurrogateClassCoverageInfo(
            value.name,
            value.level,
            value.instructionCoverage,
            value.branchCoverage,
            value.linesCoverage,
            value.complexityCoverage,
            value.methods.map { SurrogateMethodCoverageInfo.fromValue(it) }
        )
    }
}

@Serializable
@SerialName("package")
private data class SurrogatePackageCoverageInfo(
    override val name: String,
    override val level: AnalysisUnit,
    override val instructionCoverage: CoverageInfo,
    override val branchCoverage: CoverageInfo,
    override val linesCoverage: CoverageInfo,
    override val complexityCoverage: CoverageInfo,
    val classes: List<SurrogateClassCoverageInfo>,
) : SurrogateCoverageInfo() {
    val value: PackageCoverageInfo
        get() {
            val result =
                PackageCoverageInfo(name, instructionCoverage, branchCoverage, linesCoverage, complexityCoverage)
            result.classes.addAll(classes.map { it.value })
            return result
        }

    companion object {
        fun fromValue(value: PackageCoverageInfo) = SurrogatePackageCoverageInfo(
            value.name,
            value.level,
            value.instructionCoverage,
            value.branchCoverage,
            value.linesCoverage,
            value.complexityCoverage,
            value.classes.map { SurrogateClassCoverageInfo.fromValue(it) }
        )
    }
}

object MethodCoverageInfoSerializer : KSerializer<MethodCoverageInfo> {
    override val descriptor: SerialDescriptor = SurrogateMethodCoverageInfo.serializer().descriptor
    override fun serialize(encoder: Encoder, value: MethodCoverageInfo) {
        val surrogate = SurrogateMethodCoverageInfo.fromValue(value)
        encoder.encodeSerializableValue(SurrogateMethodCoverageInfo.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): MethodCoverageInfo {
        val surrogate = decoder.decodeSerializableValue(SurrogateMethodCoverageInfo.serializer())
        return surrogate.value
    }
}

object ClassCoverageInfoSerializer : KSerializer<ClassCoverageInfo> {
    override val descriptor: SerialDescriptor = SurrogateClassCoverageInfo.serializer().descriptor
    override fun serialize(encoder: Encoder, value: ClassCoverageInfo) {
        val surrogate = SurrogateClassCoverageInfo.fromValue(value)
        encoder.encodeSerializableValue(SurrogateClassCoverageInfo.serializer(), surrogate)
    }
    override fun deserialize(decoder: Decoder): ClassCoverageInfo {
        val surrogate = decoder.decodeSerializableValue(SurrogateClassCoverageInfo.serializer())
        return surrogate.value
    }
}

object PackageCoverageInfoSerializer : KSerializer<PackageCoverageInfo> {
    override val descriptor: SerialDescriptor = SurrogatePackageCoverageInfo.serializer().descriptor
    override fun serialize(encoder: Encoder, value: PackageCoverageInfo) {
        val surrogate = SurrogatePackageCoverageInfo.fromValue(value)
        encoder.encodeSerializableValue(SurrogatePackageCoverageInfo.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): PackageCoverageInfo {
        val surrogate = decoder.decodeSerializableValue(SurrogatePackageCoverageInfo.serializer())
        return surrogate.value
    }
}

