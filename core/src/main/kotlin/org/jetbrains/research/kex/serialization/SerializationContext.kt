package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.state.term.Term
import kotlin.reflect.KClass

@ImplicitReflectionSerializer
val kexTypeSerialModule: SerialModule
    get() = SerializersModule {
        polymorphic(KexType::class) {
            KexType.types.forEach { (_, klass) ->
                @Suppress("UNCHECKED_CAST") val any = klass as KClass<Any>
                addSubclass(any, any.serializer())
            }
        }
    }

@ImplicitReflectionSerializer
val termSerialModule: SerialModule
    get() = SerializersModule {
        include(kfgSerialModule)
        include(kexTypeSerialModule)
        polymorphic(Term::class) {
            Term.terms.forEach { (_, klass) ->
                @Suppress("UNCHECKED_CAST") val any = klass.kotlin as KClass<Any>
                addSubclass(any, any.serializer())
            }
        }

    }

@ImplicitReflectionSerializer
val predicateTypeSerialModule: SerialModule
    get() = SerializersModule {
        polymorphic(PredicateType::class) {
            addSubclass(PredicateType.Path::class, PredicateType.Path.serializer())
            addSubclass(PredicateType.State::class, PredicateType.State.serializer())
            addSubclass(PredicateType.Assume::class, PredicateType.Assume.serializer())
            addSubclass(PredicateType.Require::class, PredicateType.Require.serializer())
        }
    }

@ImplicitReflectionSerializer
val predicateSerialModule: SerialModule
    get() = SerializersModule {
        include(termSerialModule)
        include(predicateTypeSerialModule)
        polymorphic(Predicate::class) {
            Predicate.predicates.forEach { (_, klass) ->
                @Suppress("UNCHECKED_CAST") val any = klass.kotlin as KClass<Any>
                addSubclass(any, any.serializer())
            }
        }
    }