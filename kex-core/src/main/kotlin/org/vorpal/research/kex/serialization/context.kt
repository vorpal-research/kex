package org.vorpal.research.kex.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ClassDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.FieldContainingDescriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.trace.symbolic.Clause
import org.vorpal.research.kex.trace.symbolic.PathClause
import org.vorpal.research.kex.trace.symbolic.StateClause
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.SymbolicStateImpl
import org.vorpal.research.kex.trace.symbolic.WrappedValue
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext
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

@InternalSerializationApi
fun getTermSerialModule(cm: ClassManager, ctx: NameMapperContext): SerializersModule = SerializersModule {
    include(getKfgSerialModule(cm, ctx))
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

@InternalSerializationApi
fun getPredicateSerialModule(cm: ClassManager, ctx: NameMapperContext): SerializersModule = SerializersModule {
    include(getTermSerialModule(cm, ctx))
    include(predicateTypeSerialModule)
    polymorphic(Predicate::class) {
        for (klass in Predicate.predicates.values) {
            @Suppress("UNCHECKED_CAST")
            val any = klass.kotlin as KClass<Predicate>
            subclass(any, any.serializer())
        }
    }
}

@InternalSerializationApi
fun getPredicateStateSerialModule(cm: ClassManager, ctx: NameMapperContext): SerializersModule = SerializersModule {
    include(getPredicateSerialModule(cm, ctx))
    polymorphic(PredicateState::class) {
        for (klass in PredicateState.states.values) {
            @Suppress("UNCHECKED_CAST")
            val any = klass.kotlin as KClass<PredicateState>
            subclass(any, any.serializer())
        }
    }
}

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

@InternalSerializationApi
fun getSymbolicStateSerialModule(): SerializersModule = SerializersModule {
    polymorphic(Clause::class) {
        subclass(StateClause::class, StateClause.serializer())
        subclass(PathClause::class, PathClause.serializer())
    }
    polymorphic(SymbolicState::class) {
        subclass(SymbolicStateImpl::class, SymbolicStateImpl.serializer())
    }
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getPreSymbolicSerialModule(cm: ClassManager, ctx: NameMapperContext): SerializersModule = SerializersModule {
    val base = getPredicateStateSerialModule(cm, ctx)
    include(base)
    include(getDescriptorSerialModule())
    contextual(
        WrappedValue::class, WrappedValueSerializer(
            ctx,
            base.getContextual(Method::class)!!
        )
    )
}

@ExperimentalSerializationApi
@InternalSerializationApi
fun getKexSerialModule(cm: ClassManager, ctx: NameMapperContext): SerializersModule = SerializersModule {
    include(getPreSymbolicSerialModule(cm, ctx))
    include(getSymbolicStateSerialModule())
}
