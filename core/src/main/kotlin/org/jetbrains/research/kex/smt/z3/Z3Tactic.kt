package org.jetbrains.research.kex.smt.z3

import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kex.util.unreachable
import java.io.File

private val tacticsFile = GlobalConfig.getStringValue("z3.tacticsFile", "")

private fun <T> Klaxon.convert(k: kotlin.reflect.KClass<*>,
                               fromJson: (JsonValue) -> T,
                               toJson: (T) -> String, isUnion: Boolean = false) =
        this.converter(object : Converter {
            @Suppress("UNCHECKED_CAST")
            override fun toJson(value: Any) = toJson(value as T)

            override fun fromJson(jv: JsonValue) = fromJson(jv) as Any
            override fun canConvert(cls: Class<*>) = cls == k.java || (isUnion && cls.superclass == k.java)
        })

private val klaxon = Klaxon()
        .convert(Value::class, { Value.fromJson(it) }, { it.toJson() }, true)

class Z3Tactics(elements: Collection<Z3Tactic>) : ArrayList<Z3Tactic>(elements) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun load(): Z3Tactics = fromJson(File(tacticsFile).readText())

        fun fromJson(json: String) = Z3Tactics(klaxon.parseArray(json)
                ?: unreachable { loggerFor(Z3Tactics::class).error("Cannot parse Z3Tactics from string $json") })
    }
}

data class Z3Tactic(
        val name: String,
        val type: String,
        val params: List<Param>
)

data class Param(
        val key: String,
        val value: Value
)

sealed class Value {
    class BoolValue(val value: Boolean) : Value()
    class IntValue(val value: Int) : Value()
    class DoubleValue(val value: Double) : Value()
    class StringValue(val value: String) : Value()

    fun toJson(): String = klaxon.toJsonString(when (this) {
        is BoolValue -> this.value
        is IntValue -> this.value
        is DoubleValue -> this.value
        is StringValue -> this.value
    })

    companion object {
        fun fromJson(jv: JsonValue): Value = when (jv.inside) {
            is Boolean -> BoolValue(jv.boolean!!)
            is Int -> IntValue(jv.int!!)
            is Double -> DoubleValue(jv.double!!)
            is String -> StringValue(jv.string!!)
            else -> throw IllegalArgumentException()
        }
    }
}
