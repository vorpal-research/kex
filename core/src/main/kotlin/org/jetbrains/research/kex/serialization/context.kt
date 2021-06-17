package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ClassManager
import kotlin.reflect.KClass

@InternalSerializationApi
val kexTypeSerialModule: SerializersModule
    get() = SerializersModule {
        polymorphic(KexType::class) {
            for (klass in KexType.types.values) {
                if (!klass.isAbstract && !klass.isSealed)
                    subclass(klass, klass.serializer())
            }
        }
    }

@ExperimentalSerializationApi
@InternalSerializationApi
fun getTermSerialModule(cm: ClassManager): SerializersModule = SerializersModule {
    include(getKfgSerialModule(cm))
    include(kexTypeSerialModule)
    polymorphic(Term::class) {
        for (klass in Term.terms.values) {
            @Suppress("UNCHECKED_CAST")
            val any = klass.kotlin as KClass<Term>
            subclass(any, any.serializer())
        }
    }
}

val predicateTypeSerialModule: SerializersModule
    get() = SerializersModule {
        polymorphic(PredicateType::class) {
            subclass(PredicateType.Path::class, PredicateType.Path.serializer())
            subclass(PredicateType.State::class, PredicateType.State.serializer())
            subclass(PredicateType.Assume::class, PredicateType.Assume.serializer())
            subclass(PredicateType.Axiom::class, PredicateType.Axiom.serializer())
            subclass(PredicateType.Require::class, PredicateType.Require.serializer())
        }
    }

@ExperimentalSerializationApi
@InternalSerializationApi
fun getPredicateSerialModule(cm: ClassManager): SerializersModule = SerializersModule {
    include(getTermSerialModule(cm))
    include(predicateTypeSerialModule)
    polymorphic(Predicate::class) {
        for (klass in Predicate.predicates.values) {
            @Suppress("UNCHECKED_CAST")
            val any = klass.kotlin as KClass<Predicate>
            subclass(any, any.serializer())
        }
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getPredicateStateSerialModule(cm: ClassManager): SerializersModule = SerializersModule {
    include(getPredicateSerialModule(cm))
    polymorphic(PredicateState::class) {
        for (klass in PredicateState.states.values) {
            @Suppress("UNCHECKED_CAST")
            val any = klass.kotlin as KClass<PredicateState>
            subclass(any, any.serializer())
        }
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getKexSerialModule(cm: ClassManager): SerializersModule = SerializersModule {
    include(getPredicateStateSerialModule(cm))
    contextual(Descriptor::class, DescriptorSerializer)
    contextual(ConstantDescriptor.Null::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Bool::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Byte::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Char::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Short::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Int::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Long::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Float::class, DescriptorSerializer.to())
    contextual(ConstantDescriptor.Double::class, DescriptorSerializer.to())
    contextual(ObjectDescriptor::class, DescriptorSerializer.to())
    contextual(ClassDescriptor::class, DescriptorSerializer.to())
    contextual(ArrayDescriptor::class, DescriptorSerializer.to())
}