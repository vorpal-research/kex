package org.vorpal.research.kex.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.jacoco.CommonCoverageInfo
import org.vorpal.research.kthelper.tryOrNull
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

    private fun init(): Map<Pair<String, String>, CoverageRes> {
        val permanentCoverageInfoFile = kexConfig.getPathValue("kex", "coverage", "coverage.json")
        return when {
            permanentCoverageInfoFile.exists() -> tryOrNull {
                json.decodeFromString(permanentCoverageInfoFile.readText())
            } ?: emptyMap()
            else -> emptyMap()
        }
    }

    fun emit() {
        val permanentCoverageInfoFile = kexConfig.getPathValue("kex", "coverage", "coverage.json")
        tryOrNull {
            json.encodeToString(
                MapSerializer(
                    PairSerializer(String.serializer(), String.serializer()),
                    CoverageRes.serializer()
                ), permanentInfo
            )
        }?.apply {
            permanentCoverageInfoFile.writeText(this)
        }
    }

    fun putNewInfo(mode: String, target: String, coverageInfo: CommonCoverageInfo) {
        permanentInfo[target to mode] = CoverageRes(
            coverageInfo.instructionCoverage.ratio,
            coverageInfo.branchCoverage.ratio,
            coverageInfo.linesCoverage.ratio,
            coverageInfo.complexityCoverage.ratio
        )
    }
}
