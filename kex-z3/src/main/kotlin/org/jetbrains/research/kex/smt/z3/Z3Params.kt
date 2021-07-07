package org.jetbrains.research.kex.smt.z3

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import java.io.File

private val paramFile by lazy {
    kexConfig.getStringValue("z3", "paramFile")
        ?: unreachable { log.error("You need to specify parameters file to be able to use Z3 SMT") }
}

@Serializable
class Z3Params(private val elements: List<Z3Param>) : List<Z3Param> by elements {
    fun toJson() = Json.encodeToString(this)

    companion object {
        fun load() = fromJson(File(paramFile).readText())

        fun fromJson(json: String) = Json.decodeFromString<Z3Params>(json)
    }
}

@Serializable
data class Z3Param(
    val key: String,
    val value: Value
)

@Serializable
sealed class Value {
    @Serializable
    data class BoolValue(val value: Boolean) : Value() {
        override fun toString() = value.toString()
    }

    @Serializable
    data class IntValue(val value: Int) : Value() {
        override fun toString() = value.toString()
    }

    @Serializable
    data class DoubleValue(val value: Double) : Value() {
        override fun toString() = value.toString()
    }

    @Serializable
    data class StringValue(val value: String) : Value() {
        override fun toString() = value
    }
}
