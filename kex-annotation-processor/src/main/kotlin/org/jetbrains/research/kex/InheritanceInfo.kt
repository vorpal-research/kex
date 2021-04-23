package org.jetbrains.research.kex

import org.jetbrains.research.kthelper.`try`
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class InheritanceInfo(
        val base: String,
        val inheritors: Set<Inheritor>
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = `try` { klaxon.parse<InheritanceInfo>(json)!! }.map {
            InheritanceInfo(it.base, it.inheritors.toMutableSet())
        }.getOrNull()
    }

    operator fun plus(inheritor: Inheritor) = InheritanceInfo(base, inheritors + inheritor)
}

data class Inheritor(
        val name: String,

        @Json(name = "class")
        val inheritorClass: String
)


