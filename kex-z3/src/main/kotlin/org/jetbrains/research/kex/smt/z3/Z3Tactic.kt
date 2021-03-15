package org.jetbrains.research.kex.smt.z3

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import org.jetbrains.research.kex.config.kexConfig
import java.io.File

private val tacticsFile by lazy {
    kexConfig.getStringValue("z3", "tacticsFile")
            ?: unreachable { log.error("You need to specify tactics file to be able to use Z3 SMT") }
}

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
        .convert(Z3Params::class, { Z3Params.fromJson(it) }, { it.toJson() }, true)

class Z3Tactics(elements: Collection<Z3Tactic>) : ArrayList<Z3Tactic>(elements) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun load(): Z3Tactics = fromJson(File(tacticsFile).readText())

        fun fromJson(json: String) = Z3Tactics(klaxon.parseArray(json)
                ?: unreachable { log.error("Cannot parse Z3Tactics from string $json") })
    }
}

data class Z3Tactic(
        val name: String,
        val type: String,
        val params: Z3Params
)

