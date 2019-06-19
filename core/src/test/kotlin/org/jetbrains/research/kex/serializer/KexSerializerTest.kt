package org.jetbrains.research.kex.serializer

//import org.jetbrains.research.kex.serialization.predicateTypeSerialModule
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import org.jetbrains.research.kex.KexTest
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateFactory
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.FieldLoadTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.TermFactory
import org.jetbrains.research.kfg.ir.value.instruction.CmpOpcode
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
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
        val tf = TermFactory
        val boolTerm = tf.getBool(true)
        val klassType = KexClass("org/jetbrains/research/kex/Test")
        val valueTerm = tf.getValue(klassType, "testValue")
        val arrayType = KexArray(KexDouble(), memspace = 42)
        val fieldName = tf.getString("mySuperAwesomeField")
        val fieldTerm = tf.getField(KexReference(arrayType), valueTerm, fieldName)
        val fieldLoadTerm = tf.getFieldLoad(arrayType, fieldTerm)

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
    fun testPredicateTypeSerialization() {
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
    fun testPredicateStateSerialization() {
        val basicClass = cm.getByName("$packageName/BasicTests")

        for ((_, method) in basicClass.methods) {
            val psa = getPSA(method)
            val state = psa.builder(method).getInstructionState(method.flatten().first { it is ReturnInst }) ?: continue

            val serializedState = serializer.toJson(state)
            val deserializedState = serializer.fromJson<PredicateState>(serializedState)

            assertEquals(state, deserializedState)
        }
    }
}