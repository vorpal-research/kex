package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.MockDescriptor
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.reanimator.actionsequence.*
import org.vorpal.research.kfg.UnknownInstanceException
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.logging.log

class MockGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor): Boolean = descriptor is MockDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence =
        with(context) {
            descriptor as? MockDescriptor
                ?: throw IllegalArgumentException("Expected MockDescriptor. Got: $descriptor")
            descriptor as MockDescriptor // helps autocompletion

            val name = "${descriptor.term}"
            val actionSequence = MockSequence(name)
            saveToCache(descriptor, actionSequence)
            val kfgClass = (descriptor.type.getKfgType(types) as ClassType).klass
            val extraInterfaces = descriptor.extraInterfaces
                .map { (it.getKfgType(types) as ClassType).klass }
                .toSet()
            actionSequence.mockCalls.add(MockNewInstance(kfgClass, extraInterfaces))

            for ((method, returnValuesDesc) in descriptor.methodReturns) {
                val returnValues = returnValuesDesc.map { value -> fallback.generate(value) }
                actionSequence.mockCalls += MockSetupMethod(method, returnValues)
            }

            actionSequence.reflectionCalls.addSetupFieldsCalls(
                descriptor.fields,
                kfgClass,
                types,
                fallback
            )

            getFromCache(descriptor)!!
        }


}

fun MutableList<ReflectionCall>.addSetupFieldsCalls(
    fields: MutableMap<Pair<String, KexType>, Descriptor>,
    kfgClass: Class,
    types: TypeFactory,
    fallback: Generator
) {
    for ((field, value) in fields) {
        val fieldType = field.second.getKfgType(types).rtUnmapped
        val kfgField = try {
            kfgClass.getField(field.first, fieldType)
        } catch (e: UnknownInstanceException) {
            log.warn("Field ${field.first}: ${field.second} is not found in class $kfgClass")
            continue
        }
        val valueAS = fallback.generate(value)
        this += ReflectionSetField(kfgField, valueAS)
    }
}
