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
import org.jetbrains.research.kex.trace.symbolic.*
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Method
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
fun getDescriptorSerialModule(): SerializersModule = SerializersModule {
    val descriptorSerializer = DescriptorSerializer()
    contextual(Descriptor::class, descriptorSerializer)
    contextual(ConstantDescriptor::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Null::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Bool::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Byte::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Char::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Short::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Int::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Long::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Float::class, descriptorSerializer.to())
    contextual(ConstantDescriptor.Double::class, descriptorSerializer.to())
    contextual(FieldContainingDescriptor::class, descriptorSerializer.to())
    contextual(ObjectDescriptor::class, descriptorSerializer.to())
    contextual(ClassDescriptor::class, descriptorSerializer.to())
    contextual(ArrayDescriptor::class, descriptorSerializer.to())
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getSymbolicStateSerialModule(serializersModule: SerializersModule): SerializersModule = SerializersModule {
    contextual(ConcreteTermMap::class, mapSerializer(serializersModule, { it }, { ConcreteTermMap(it) }))
    contextual(ValueTermMap::class, mapSerializer(serializersModule, { it }, { ValueTermMap(it) }))
    contextual(ValuePredicateMap::class, mapSerializer(serializersModule, { it }, { ValuePredicateMap(it) }))
    polymorphic(PathCondition::class) {
        subclass(PathConditionImpl::class, PathConditionImpl.serializer())
    }
    polymorphic(SymbolicState::class) {
        subclass(SymbolicStateImpl::class, SymbolicStateImpl.serializer())
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getPreSymbolicSerialModule(cm: ClassManager): SerializersModule = SerializersModule {
    val base = getPredicateStateSerialModule(cm)
    include(base)
    include(getDescriptorSerialModule())
    contextual(WrappedValue::class, WrappedValueSerializer(
        base.getContextual(Method::class)!!
    ))
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getKexSerialModule(cm: ClassManager): SerializersModule = SerializersModule {
    val module = getPreSymbolicSerialModule(cm)
    include(module)
    include(getSymbolicStateSerialModule(module))
}