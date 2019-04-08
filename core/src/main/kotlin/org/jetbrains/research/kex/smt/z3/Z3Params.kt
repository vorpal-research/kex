package org.jetbrains.research.kex.smt.z3

import com.beust.klaxon.*
import org.jetbrains.research.kex.config.GlobalConfig
import java.io.File

private val paramFile = GlobalConfig.getStringValue("z3", "paramFile")

private fun <T> Klaxon.convert(k: kotlin.reflect.KClass<*>, fromJson: (JsonValue) -> T, toJson: (T) -> String, isUnion: Boolean = false) =
        this.converter(object: Converter {
            @Suppress("UNCHECKED_CAST")
            override fun toJson(value: Any)        = toJson(value as T)
            override fun fromJson(jv: JsonValue)   = fromJson(jv) as Any
            override fun canConvert(cls: Class<*>) = cls == k.java || (isUnion && cls.superclass == k.java)
        })

private val klaxon = Klaxon()
        .convert(Value::class, { Value.fromJson(it) }, { it.toJson() }, true)

class Z3Params(elements: Collection<Z3Param>) : ArrayList<Z3Param>(elements) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun load() = fromJson(File(paramFile).readText())

        fun fromJson(json: String) = Z3Params(klaxon.parseArray(json)!!)

        fun fromJson(jv: JsonValue): Z3Params = when (jv.inside) {
            is JsonArray<*> -> {
                val params = arrayListOf<Z3Param>()
                val array = jv.array!!
                for (any in array) {
                    val `object` = any as? JsonObject ?: throw java.lang.IllegalArgumentException()
                    val key = `object`.string("key") ?: throw java.lang.IllegalArgumentException()
                    val value = Value.fromJson(`object`)
                    params.add(Z3Param(key, value))
                }
                Z3Params(params)
            }
            else -> throw IllegalArgumentException()
        }
    }
}

data class Z3Param (
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

        fun fromJson(jo: JsonObject): Value {
            val inner = jo["value"]
            return when (inner) {
                is Boolean -> BoolValue(inner)
                is Int -> IntValue(inner)
                is Double -> DoubleValue(inner)
                is String -> StringValue(inner)
                else -> throw IllegalArgumentException()
            }
        }
    }
}
