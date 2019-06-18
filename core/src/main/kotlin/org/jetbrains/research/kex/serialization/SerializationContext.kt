package org.jetbrains.research.kex.serialization

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.state.term.Term
import kotlin.reflect.KClass

@ImplicitReflectionSerializer
val kexTypeSerializationContext: SerialModule
    get() = SerializersModule {
        polymorphic(KexType::class) {
            KexType.types.forEach { (_, klass) ->
                @Suppress("UNCHECKED_CAST") val any = klass as KClass<Any>
                addSubclass(any, any.serializer())
            }
        }
    }

@ImplicitReflectionSerializer
val termSerializationContext: SerialModule
    get() = SerializersModule {
        include(kfgSerializationContext)
        include(kexTypeSerializationContext)
        polymorphic(Term::class) {
            Term.terms.forEach { (_, klass) ->
                @Suppress("UNCHECKED_CAST") val any = klass.kotlin as KClass<Any>
                addSubclass(any, any.serializer())
            }
        }

    }