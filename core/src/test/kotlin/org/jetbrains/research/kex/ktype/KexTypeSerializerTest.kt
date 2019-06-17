package org.jetbrains.research.kex.ktype

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

class KexTypeSerializerTest {

    @ImplicitReflectionSerializer
    @Test
    fun serializationTest() {

        val voidType = KexVoid()
        val intType = KexInt()
        val doubleType = KexDouble()
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val arrayType = KexArray(klassType, memspace = 15)

        val json = Json(JsonConfiguration.Stable, context = SerializersModule {
            polymorphic(KexType::class) {
                KexType.types.forEach { (_, klass) ->
                    @Suppress("UNCHECKED_CAST") val any = klass as KClass<Any>
                    addSubclass(any, any.serializer())
                }
            }
        })

        val serializedVoid = json.stringify(KexType.serializer(), voidType)
        val serializedInt = json.stringify(KexType.serializer(), intType)
        val serializedDouble = json.stringify(KexType.serializer(), doubleType)
        val serializedKlass = json.stringify(KexType.serializer(), klassType)
        val serializedArray = json.stringify(KexType.serializer(), arrayType)

        val deserializedVoid = json.parse(KexType.serializer(), serializedVoid)
        val deserializedInt = json.parse(KexType.serializer(), serializedInt)
        val deserializedDouble = json.parse(KexType.serializer(), serializedDouble)
        val deserializedKlass = json.parse(KexType.serializer(), serializedKlass)
        val deserializedArray = json.parse(KexType.serializer(), serializedArray)

        assertEquals(voidType, deserializedVoid)
        assertEquals(intType, deserializedInt)
        assertEquals(doubleType, deserializedDouble)
        assertEquals(klassType, deserializedKlass)
        assertEquals(arrayType, deserializedArray)
    }
}