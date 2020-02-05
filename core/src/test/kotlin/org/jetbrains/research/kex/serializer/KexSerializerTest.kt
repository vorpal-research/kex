package org.jetbrains.research.kex.serializer

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableDefault
@ImplicitReflectionSerializer
class KexSerializerTest : KexTest() {
    val serializer = KexSerializer(cm)

    @Test
    fun typeSerializationTest() {
        val voidType = KexVoid()
        val intType = KexInt()
        val doubleType = KexDouble()
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val arrayType = KexArray(klassType, memspace = 15)

        val serializedVoid = serializer.toJson<KexType>(voidType)
        val serializedInt = serializer.toJson<KexType>(intType)
        val serializedDouble = serializer.toJson<KexType>(doubleType)
        val serializedKlass = serializer.toJson<KexType>(klassType)
        val serializedArray = serializer.toJson<KexType>(arrayType)

        val deserializedVoid = serializer.fromJson<KexType>(serializedVoid)
        val deserializedInt = serializer.fromJson<KexType>(serializedInt)
        val deserializedDouble = serializer.fromJson<KexType>(serializedDouble)
        val deserializedKlass = serializer.fromJson<KexType>(serializedKlass)
        val deserializedArray = serializer.fromJson<KexType>(serializedArray)

        assertEquals(voidType, deserializedVoid)
        assertEquals(intType, deserializedInt)
        assertEquals(doubleType, deserializedDouble)
        assertEquals(klassType, deserializedKlass)
        assertEquals(arrayType, deserializedArray)
    }

    @Test
    fun termSerializationTest() {
        val boolTerm = term { const(true) }
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val valueTerm = term { value(klassType, "testValue") }
        val arrayType = KexArray(KexDouble(), memspace = 42)
        val fieldName = term { const("mySuperAwesomeField") }
        val fieldTerm = term { valueTerm.field(arrayType, fieldName) }
        val fieldLoadTerm = term { fieldTerm.load() }

        val serializedBool = serializer.toJson<Term>(boolTerm)
        val serializedValue = serializer.toJson<Term>(valueTerm)
        val serializedFieldName = serializer.toJson<Term>(fieldName)
        val serializedField = serializer.toJson<Term>(fieldTerm)
        val serializedFieldLoad = serializer.toJson<Term>(fieldLoadTerm)

        val deserializedBool = serializer.fromJson<Term>(serializedBool)
        val deserializedValue = serializer.fromJson<Term>(serializedValue)
        val deserializedFieldName = serializer.fromJson<Term>(serializedFieldName)
        val deserializedField = serializer.fromJson<Term>(serializedField)
        val deserializedFieldLoad = serializer.fromJson<Term>(serializedFieldLoad)

        assertEquals(boolTerm, deserializedBool)
        assertEquals(valueTerm, deserializedValue)
        assertEquals(fieldName, deserializedFieldName)
        assertEquals(fieldTerm, deserializedField)
        assertEquals(fieldLoadTerm, deserializedFieldLoad)
        assertEquals((deserializedFieldLoad as FieldLoadTerm).field, fieldTerm)
    }

    @Test
    fun predicateTypeSerializationTest() {
        val state = PredicateType.State()
        val path = PredicateType.Path()
        val assume = PredicateType.Assume()
        val require = PredicateType.Require()

        val serializedState = serializer.toJson<PredicateType>(state)
        val serializedPath = serializer.toJson<PredicateType>(path)
        val serializedAssume = serializer.toJson<PredicateType>(assume)
        val serializedRequire = serializer.toJson<PredicateType>(require)

        val deserializedState = serializer.fromJson<PredicateType>(serializedState)
        val deserializedPath = serializer.fromJson<PredicateType>(serializedPath)
        val deserializedAssume = serializer.fromJson<PredicateType>(serializedAssume)
        val deserializedRequire = serializer.fromJson<PredicateType>(serializedRequire)
        assertEquals(state, deserializedState)
        assertEquals(path, deserializedPath)
        assertEquals(assume, deserializedAssume)
        assertEquals(require, deserializedRequire)
    }

    @Test
    fun predicateSerializationTest() {
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val argTerm = term { arg(KexInt(), 0) }
        val constantInt = term { const(137) }
        val equalityPredicate = state { argTerm equality constantInt }
        val cmpTerm = term { argTerm gt 0 }
        val pathTerm = term { value(KexBool(), "path") }
        val assignPredicate = state { pathTerm equality cmpTerm }
        val pathPredicate = path { pathTerm equality true }
        val assumePredicate = assume { `this`(klassType) inequality null }

        val serializedEq = serializer.toJson<Predicate>(equalityPredicate)
        val serializedAssign = serializer.toJson<Predicate>(assignPredicate)
        val serializedPath = serializer.toJson<Predicate>(pathPredicate)
        val serializedAssume = serializer.toJson<Predicate>(assumePredicate)

        val deserializedEq = serializer.fromJson<Predicate>(serializedEq)
        val deserializedAssign = serializer.fromJson<Predicate>(serializedAssign)
        val deserializedPath = serializer.fromJson<Predicate>(serializedPath)
        val deserializedAssume = serializer.fromJson<Predicate>(serializedAssume)

        assertEquals(equalityPredicate, deserializedEq)
        assertEquals(assignPredicate, deserializedAssign)
        assertEquals(pathPredicate, deserializedPath)
        assertEquals(assumePredicate, deserializedAssume)
    }

    @Test
    fun predicateStateSerializationTest() {
        val basicClass = cm.getByName("$packageName/BasicTests")

        for (method in basicClass.allMethods) {
            val psa = getPSA(method)
            val state = psa.builder(method).methodState ?: continue

            val serializedState = serializer.toJson(state)
            val deserializedState = serializer.fromJson<PredicateState>(serializedState)

            assertEquals(state, deserializedState)
        }
    }
}