package org.vorpal.research.kex.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.CommonCoverageInfo
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object PermanentCoverageInfo {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        prettyPrint = true
        useArrayPolymorphism = false
        classDiscriminator = "className"
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
    }
    private val permanentInfo = init().toMutableMap()

    @Serializable
    private data class CoverageRes(
        val instructions: Double,
        val branches: Double,
        val lines: Double,
        val complexity: Double
    )

    private fun init(): Map<String, CoverageRes> {
        val permanentCoverageInfoFile = kexConfig.getPathValue("kex", "coverage", "coverage.json")
        return when {
            permanentCoverageInfoFile.exists() -> json.decodeFromString(permanentCoverageInfoFile.readText())
            else -> emptyMap()
        }
    }

    fun emit() {
        val permanentCoverageInfoFile = kexConfig.getPathValue("kex", "coverage", "coverage.json")
        permanentCoverageInfoFile.writeText(
            json.encodeToString(
                MapSerializer(
                    String.serializer(),
                    CoverageRes.serializer()
                ), permanentInfo
            )
        )
    }

    fun putNewInfo(target: String, coverageInfo: CommonCoverageInfo) {
        permanentInfo[target] = CoverageRes(
            coverageInfo.instructionCoverage.ratio,
            coverageInfo.branchCoverage.ratio,
            coverageInfo.linesCoverage.ratio,
            coverageInfo.complexityCoverage.ratio
        )
    }
}
