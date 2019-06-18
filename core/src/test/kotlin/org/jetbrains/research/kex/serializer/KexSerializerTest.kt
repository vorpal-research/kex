package org.jetbrains.research.kex.serializer

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.serialization.kexTypeSerializationContext
import org.jetbrains.research.kex.serialization.termSerializationContext
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.junit.Assert.assertEquals
import org.junit.Test

@ImplicitReflectionSerializer
class KexSerializerTest {
    @UseExperimental(UnstableDefault::class)
    val configuration = JsonConfiguration(
            encodeDefaults = false,
            strictMode = true,
            unquoted = false,
            prettyPrint = true,
            indent = "  ",
            useArrayPolymorphism = false,
            classDiscriminator = "className"
    )

    @Test
    fun typeSerializationTest() {
        val voidType = KexVoid()
        val intType = KexInt()
        val doubleType = KexDouble()
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val arrayType = KexArray(klassType, memspace = 15)

        val json = Json(configuration, kexTypeSerializationContext)

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

    @Test
    fun termSerializationTest() {
        val tf = TermFactory
        val boolTerm = tf.getBool(true)
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val valueTerm = tf.getValue(klassType, "testValue")
        val arrayType = KexArray(KexDouble(), memspace = 42)
        val fieldName = tf.getString("mySuperAwesomeField")
        val fieldTerm = tf.getField(KexReference(arrayType), valueTerm, fieldName)
        val fieldLoadTerm = tf.getFieldLoad(arrayType, fieldTerm)

        val json = Json(configuration, termSerializationContext)

        val serializedBool = json.stringify(Term.serializer(), boolTerm)
        val serializedValue = json.stringify(Term.serializer(), valueTerm)
        val serializedFieldName = json.stringify(Term.serializer(), fieldName)
        val serializedField = json.stringify(Term.serializer(), fieldTerm)
        val serializedFieldLoad = json.stringify(Term.serializer(), fieldLoadTerm)

        val deserializedBool = json.parse(Term.serializer(), serializedBool)
        val deserializedValue = json.parse(Term.serializer(), serializedValue)
        val deserializedFieldName = json.parse(Term.serializer(), serializedFieldName)
        val deserializedField = json.parse(Term.serializer(), serializedField)
        val deserializedFieldLoad = json.parse(Term.serializer(), serializedFieldLoad)

        assertEquals(boolTerm, deserializedBool)
        assertEquals(valueTerm, deserializedValue)
        assertEquals(fieldName, deserializedFieldName)
        assertEquals(fieldTerm, deserializedField)
        assertEquals(fieldLoadTerm, deserializedFieldLoad)
        assertEquals((deserializedFieldLoad as FieldLoadTerm).field, fieldTerm)
    }
}