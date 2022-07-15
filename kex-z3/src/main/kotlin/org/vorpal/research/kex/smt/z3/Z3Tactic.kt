package org.vorpal.research.kex.smt.z3

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.io.File

private val tacticsFile by lazy {
    kexConfig.getStringValue("z3", "tacticsFile")
}

@Serializable
class Z3Tactics(private val elements: List<Z3Tactic>) : List<Z3Tactic> by elements {
    fun toJson() = Json.encodeToString(this)

    companion object {
        fun load(): Z3Tactics = tacticsFile?.let {
            val file = File(it)
            return when {
                file.exists() -> fromJson(file.readText())
                else -> Z3Tactics(emptyList())
            }
        } ?: Z3Tactics(emptyList())

        fun fromJson(json: String) = Json.decodeFromString<Z3Tactics>(json)
    }
}

@Serializable
data class Z3Tactic(
    val name: String,
    val type: String,
    val params: Z3Params
)
