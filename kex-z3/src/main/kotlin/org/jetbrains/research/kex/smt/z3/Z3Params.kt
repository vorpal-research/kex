package org.jetbrains.research.kex.smt.z3

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import com.beust.klaxon.*
import org.jetbrains.research.kex.config.kexConfig
import java.io.File

private val paramFile by lazy {
    kexConfig.getStringValue("z3", "paramFile")
            ?: unreachable { log.error("You need to specify parameters file to be able to use Z3 SMT") }
}

private fun <T> Klaxon.convert(k: kotlin.reflect.KClass<*>, fromJson: (JsonValue) -> T, toJson: (T) -> String, isUnion: Boolean = false) =
        this.converter(object : Converter {
            @Suppress("UNCHECKED_CAST")
            override fun toJson(value: Any) = toJson(value as T)

            override fun fromJson(jv: JsonValue) = fromJson(jv) as Any
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
                val params = jv.array?.let {
                    it.mapNotNull { it as? JsonObject }
                            .map { Z3Param.fromJson(it) }
                            .toList()
                } ?: throw IllegalArgumentException()
                Z3Params(params)
            }
            else -> throw IllegalArgumentException()
        }
    }
}

data class Z3Param(
        val key: String,
        val value: Value
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(jo: JsonObject): Z3Param {
            val key = jo.string("key") ?: throw java.lang.IllegalArgumentException()
            val value = when (val anyValue = jo["value"]) {
                is Boolean -> Value.BoolValue(anyValue)
                is Int -> Value.IntValue(anyValue)
                is Double -> Value.DoubleValue(anyValue)
                is String -> Value.StringValue(anyValue)
                else -> throw IllegalArgumentException()
            }
            return Z3Param(key, value)
        }
    }
}

sealed class Value {
    class BoolValue(val value: Boolean) : Value() {
        override fun toString() = value.toString()
    }

    class IntValue(val value: Int) : Value() {
        override fun toString() = value.toString()
    }

    class DoubleValue(val value: Double) : Value() {
        override fun toString() = value.toString()
    }

    class StringValue(val value: String) : Value() {
        override fun toString() = value
    }

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
