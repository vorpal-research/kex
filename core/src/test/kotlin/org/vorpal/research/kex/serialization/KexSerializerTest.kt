package org.vorpal.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.vorpal.research.kex.KexTest
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.DescriptorBuilder
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.KexVoid
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.FieldLoadTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kfg.stringClass
import kotlin.test.assertTrue

@ExperimentalSerializationApi
@InternalSerializationApi
class KexSerializerTest : KexTest("kex-serializer") {
    private val serializer = KexSerializer(cm)

    @Test
    fun typeSerializationTest() {
        val klassType = KexClass("org/vorpal/research/kex/Test")
        val arrayType = KexArray(klassType, memspace = 15)

        val serializedVoid = serializer.toJson<KexType>(KexVoid)
        val serializedInt = serializer.toJson<KexType>(KexInt)
        val serializedDouble = serializer.toJson<KexType>(KexDouble)
        val serializedKlass = serializer.toJson<KexType>(klassType)
        val serializedArray = serializer.toJson<KexType>(arrayType)

        val deserializedVoid = serializer.fromJson<KexType>(serializedVoid)
        val deserializedInt = serializer.fromJson<KexType>(serializedInt)
        val deserializedDouble = serializer.fromJson<KexType>(serializedDouble)
        val deserializedKlass = serializer.fromJson<KexType>(serializedKlass)
        val deserializedArray = serializer.fromJson<KexType>(serializedArray)

        assertEquals(KexVoid, deserializedVoid)
        assertEquals(KexInt, deserializedInt)
        assertEquals(KexDouble, deserializedDouble)
        assertEquals(klassType, deserializedKlass)
        assertEquals(arrayType, deserializedArray)
    }

    @Test
    fun termSerializationTest() {
        val boolTerm = term { const(true) }
        val klassType = KexClass("org/vorpal/research/kex/Test")
        val valueTerm = term { value(klassType, "testValue") }
        val arrayType = KexArray(KexDouble, memspace = 42)
        val fieldTerm = term { valueTerm.field(arrayType, "mySuperAwesomeField") }
        val fieldLoadTerm = term { fieldTerm.load() }

        val serializedBool = serializer.toJson<Term>(boolTerm)
        val serializedValue = serializer.toJson<Term>(valueTerm)
        val serializedField = serializer.toJson<Term>(fieldTerm)
        val serializedFieldLoad = serializer.toJson<Term>(fieldLoadTerm)

        val deserializedBool = serializer.fromJson<Term>(serializedBool)
        val deserializedValue = serializer.fromJson<Term>(serializedValue)
        val deserializedField = serializer.fromJson<Term>(serializedField)
        val deserializedFieldLoad = serializer.fromJson<Term>(serializedFieldLoad)

        assertEquals(boolTerm, deserializedBool)
        assertEquals(valueTerm, deserializedValue)
        assertEquals(fieldTerm, deserializedField)
        assertEquals(fieldLoadTerm, deserializedFieldLoad)
        assertEquals((deserializedFieldLoad as FieldLoadTerm).field, fieldTerm)
    }

    @Test
    fun predicateTypeSerializationTest() {
        val state = PredicateType.State()
        val path = PredicateType.Path()
        val assume = PredicateType.Assume()
        val axiom = PredicateType.Axiom()
        val require = PredicateType.Require()

        val serializedState = serializer.toJson<PredicateType>(state)
        val serializedPath = serializer.toJson<PredicateType>(path)
        val serializedAssume = serializer.toJson<PredicateType>(assume)
        val serializedAxiom = serializer.toJson<PredicateType>(axiom)
        val serializedRequire = serializer.toJson<PredicateType>(require)

        val deserializedState = serializer.fromJson<PredicateType>(serializedState)
        val deserializedPath = serializer.fromJson<PredicateType>(serializedPath)
        val deserializedAssume = serializer.fromJson<PredicateType>(serializedAssume)
        val deserializedAxiom = serializer.fromJson<PredicateType>(serializedAxiom)
        val deserializedRequire = serializer.fromJson<PredicateType>(serializedRequire)
        assertEquals(state, deserializedState)
        assertEquals(path, deserializedPath)
        assertEquals(assume, deserializedAssume)
        assertEquals(axiom, deserializedAxiom)
        assertEquals(require, deserializedRequire)
    }

    @Test
    fun predicateSerializationTest() {
        val klassType = KexClass("org/vorpal/research/kex/Test")
        val argTerm = term { arg(KexInt, 0) }
        val constantInt = term { const(137) }
        val equalityPredicate = state { argTerm equality constantInt }
        val cmpTerm = term { argTerm gt 0 }
        val pathTerm = term { value(KexBool, "path") }
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
        val basicClass = cm["${`package`.concretePackage}/BasicTests"]

        for (method in basicClass.allMethods) {
            val psa = getPSA(method)
            val state = psa.builder(method).methodState ?: continue

            val serializedState = serializer.toJson(state)
            val deserializedState = serializer.fromJson<PredicateState>(serializedState)

            assertEquals(state, deserializedState)
        }
    }

    @Test
    fun descriptorSerializationTest() = with(DescriptorBuilder()) {
        val const = const(1242)
        val constJson = serializer.toJson(const)
        val constFromJson = serializer.fromJson<Descriptor>(constJson)
        assertTrue { const eq constFromJson }

        val array = array(100, KexDouble)
        array[0] = const(43.123)
        array[42] = const(-12.111111)
        array[99] = const(0.1e-10)
        val arrayJson = serializer.toJson(array)
        val arrayFromJson = serializer.fromJson<Descriptor>(arrayJson)
        assertTrue { array eq arrayFromJson }

        val instance = `object`(KexClass("org/vorpal/research/kex/test/SerializerTest"))
        instance["array" to array.type] = array
        instance["const" to const.type] = const
        instance["string" to cm.stringClass.kexType] = convertToDescriptor("test")
        val instanceJson = serializer.toJson(instance)
        val instanceFromJson = serializer.fromJson<Descriptor>(instanceJson)
        assertTrue { instance eq instanceFromJson }

        val type = KexClass("org/vorpal/research/kex/test/RecursiveSerializerTest")
        val recursiveInstance = `object`(type)
        val anotherRecursive = `object`(type)
        anotherRecursive["test" to type] = `null`
        anotherRecursive["recursion" to type] = recursiveInstance
        recursiveInstance["test" to type] = anotherRecursive
        recursiveInstance["recursion" to type] = recursiveInstance
        val recursiveJson = serializer.toJson(recursiveInstance)
        val recursiveFromJson = serializer.fromJson<Descriptor>(recursiveJson)
        assertTrue { recursiveInstance eq recursiveFromJson }
    }
}
