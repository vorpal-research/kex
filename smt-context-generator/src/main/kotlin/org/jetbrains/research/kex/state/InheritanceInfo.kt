package org.jetbrains.research.kex.state

import com.beust.klaxon.*
import org.jetbrains.research.kex.util.tryOrNull

private val klaxon = Klaxon()

data class InheritanceInfo(
        val base: String,
        val inheritors: Set<Inheritor>
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = tryOrNull { klaxon.parse<InheritanceInfo>(json) }
    }
}

data class Inheritor(
        val name: String,

        @Json(name = "class")
        val inheritorClass: String
)


