package org.jetbrains.research.kex

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InheritanceInfo(
        val base: String,
        val inheritors: Set<Inheritor>
) {
    fun toJson() = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String) = Json.decodeFromString<InheritanceInfo>(json)
    }

    operator fun plus(inheritor: Inheritor) = InheritanceInfo(base, inheritors + inheritor)
}

@Serializable
data class Inheritor(
        val name: String,
        val inheritorClass: String
)


