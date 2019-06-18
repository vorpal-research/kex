package org.jetbrains.research.kex.serializer

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.serialization.kexTypeSerialModule
import org.jetbrains.research.kex.serialization.predicateSerialModule
import org.jetbrains.research.kex.serialization.predicateTypeSerialModule
//import org.jetbrains.research.kex.serialization.predicateTypeSerialModule
import org.jetbrains.research.kex.serialization.termSerialModule
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
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

        val json = Json(configuration, kexTypeSerialModule)

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

        val json = Json(configuration, termSerialModule)

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

    @Test
    fun testPredicateTypeSerialization() {
        val state = PredicateType.State()
        val path = PredicateType.Path()
        val assume = PredicateType.Assume()
        val require = PredicateType.Require()

        val json = Json(configuration, predicateTypeSerialModule)

        val serializedState = json.stringify(PredicateType.serializer(), state)
        val serializedPath = json.stringify(PredicateType.serializer(), path)
        val serializedAssume = json.stringify(PredicateType.serializer(), assume)
        val serializedRequire = json.stringify(PredicateType.serializer(), require)

        val deserializedState = json.parse(PredicateType.serializer(), serializedState)
        val deserializedPath = json.parse(PredicateType.serializer(), serializedPath)
        val deserializedAssume = json.parse(PredicateType.serializer(), serializedAssume)
        val deserializedRequire = json.parse(PredicateType.serializer(), serializedRequire)
        assertEquals(state, deserializedState)
        assertEquals(path, deserializedPath)
        assertEquals(assume, deserializedAssume)
        assertEquals(require, deserializedRequire)
    }

    @Test
    fun testPredicateSerialization() {
        val tf = TermFactory
        val pf = PredicateFactory

        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val argTerm = tf.getArgument(KexInt(), 0)
        val constantInt = tf.getInt(137)
        val equalityPredicate = pf.getEquality(argTerm, constantInt)
        val cmpTerm = tf.getCmp(CmpOpcode.Gt(), argTerm, tf.getInt(0))
        val pathTerm = tf.getValue(KexBool(), "path")
        val assignPredicate = pf.getEquality(pathTerm, cmpTerm)
        val pathPredicate = pf.getEquality(pathTerm, tf.getBool(true), PredicateType.Path())
        val assumePredicate = pf.getInequality(tf.getThis(klassType), tf.getNull(), PredicateType.Assume())

        val json = Json(configuration, predicateSerialModule)

        val serializedEq = json.stringify(Predicate.serializer(), equalityPredicate)
        val serializedAssign = json.stringify(Predicate.serializer(), assignPredicate)
        val serializedPath = json.stringify(Predicate.serializer(), pathPredicate)
        val serializedAssume = json.stringify(Predicate.serializer(), assumePredicate)

        val deserializedEq = json.parse(Predicate.serializer(), serializedEq)
        val deserializedAssign = json.parse(Predicate.serializer(), serializedAssign)
        val deserializedPath = json.parse(Predicate.serializer(), serializedPath)
        val deserializedAssume = json.parse(Predicate.serializer(), serializedAssume)

        assertEquals(equalityPredicate, deserializedEq)
        assertEquals(assignPredicate, deserializedAssign)
        assertEquals(pathPredicate, deserializedPath)
        assertEquals(assumePredicate, deserializedAssume)
    }
}