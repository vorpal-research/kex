package org.jetbrains.research.kex

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import org.jetbrains.research.kex.util.tryOrNull

private val klaxon = Klaxon()

data class InheritanceInfo(
        val base: String,
        val inheritors: Set<Inheritor>
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = tryOrNull { klaxon.parse<InheritanceInfo>(json) }?.let {
            InheritanceInfo(it.base, it.inheritors.toMutableSet())
        }
    }

    operator fun plus(inheritor: Inheritor) = InheritanceInfo(base, inheritors + inheritor)
}

data class Inheritor(
        val name: String,

        @Json(name = "class")
        val inheritorClass: String
)


