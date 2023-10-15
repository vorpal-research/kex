package org.vorpal.research.kex.parameters

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import kotlin.random.Random

@Serializable
data class Parameters<T>(
    val instance: T?,
    val arguments: List<T>,
    val statics: Set<T> = setOf(),
    val others: Set<T> = setOf()
) {
    val asList get() = listOfNotNull(instance) + arguments + statics + others

    override fun toString(): String = buildString {
        appendLine("instance: $instance")
        if (arguments.isNotEmpty())
            appendLine("args: ${arguments.joinToString("\n")}")
        if (statics.isNotEmpty())
            appendLine("statics: ${statics.joinToString("\n")}")
        if (others.isNotEmpty()) {
            appendLine("others: ${others.joinToString("\n")}")
        }
    }
}

val Parameters<Any?>.asDescriptors: Parameters<Descriptor>
    get() {
        val context = Object2DescriptorConverter()
        return Parameters(
            context.convert(instance),
            arguments.map { context.convert(it) },
            statics.mapTo(mutableSetOf()) { context.convert(it) },
            others.mapTo(mutableSetOf()) { context.convert(it) }
        )
    }

fun Parameters<Descriptor>.concreteParameters(
    cm: ClassManager,
    accessLevel: AccessModifier,
    random: Random
) = Parameters(
    instance?.concretize(cm, accessLevel, random),
    arguments.map { it.concretize(cm, accessLevel, random) },
    statics.mapTo(mutableSetOf()) { it.concretize(cm, accessLevel, random) },
    others.mapTo(mutableSetOf()) { it.concretize(cm, accessLevel, random) }
)

fun Parameters<Descriptor>.filterStaticFinals(cm: ClassManager): Parameters<Descriptor> {
    val filteredStatics = statics
        .map { it.deepCopy() }
        .filterIsInstance<ClassDescriptor>()
        .mapNotNullTo(mutableSetOf()) { klass ->
            val kfgClass = (klass.type as KexClass).kfgClass(cm.type)
            for ((name, type) in klass.fields.keys.toSet()) {
                val field = kfgClass.getField(name, type.getKfgType(cm.type))
                if (field.isFinal) klass.remove(name to type)
            }
            when {
                klass.fields.isNotEmpty() -> klass
                else -> null
            }
        }
    return Parameters(instance, arguments, filteredStatics, others)
}

private val ignoredStatics: Set<Package> by lazy {
    kexConfig.getMultipleStringValue("testGen", "ignoreStatic").flatMapTo(mutableSetOf()) {
        val originalPackage = Package.parse(it)
        val rtMapped = Package(originalPackage.concreteName.rtMapped)
        listOf(originalPackage, rtMapped)
    }
}

fun Parameters<Descriptor>.filterIgnoredStatic(): Parameters<Descriptor> {
    val filteredStatics = statics
        .filterIsInstance<ClassDescriptor>()
        .filterTo(mutableSetOf()) { descriptor ->
            val typeName = Package.parse(descriptor.type.toString())
            ignoredStatics.all { ignored ->
                !ignored.isParent(typeName)
            }
        }
    return Parameters(instance, arguments, filteredStatics, others)
}


private fun Collection<Descriptor>.replaceUninstantiableWithMocks(
    types: TypeFactory,
    termToDescriptor: MutableMap<Term, Descriptor>
): Collection<Descriptor> {
    return map { descriptor ->
        val klass = (descriptor.type.getKfgType(types) as? ClassType)?.klass
        if (klass == null || instantiationManager.isInstantiable(klass) || descriptor.type.isKexRt) {
            descriptor
        } else {
            val mock = if (descriptor is ObjectDescriptor) {
                MockDescriptor(klass.methods, descriptor)
            } else {
                MockDescriptor(descriptor.term, descriptor.type as KexClass, klass.methods)
            }
            termToDescriptor[descriptor.term] = mock
            mock
        }
    }
}

private fun Parameters<Descriptor>.generateInitialMocks(
    types: TypeFactory, termToDescriptor: MutableMap<Term, Descriptor>
): Parameters<Descriptor> {
    val mockedArguments = arguments.replaceUninstantiableWithMocks(types, termToDescriptor).toList()
    val mockedStatics = statics.replaceUninstantiableWithMocks(types, termToDescriptor).toSet()
    val mockedOthers = others.replaceUninstantiableWithMocks(types, termToDescriptor).toSet()

    return Parameters(instance, mockedArguments, mockedStatics, mockedOthers)
}

fun Parameters<Descriptor>.generateMocks(
    methodCalls: List<Pair<CallPredicate, Descriptor>>,
    termToDescriptor: MutableMap<Term, Descriptor>,
    cm: ClassManager,
    accessLevel: AccessModifier
): Parameters<Descriptor> {
    val withMocks = this.generateInitialMocks(cm.type, termToDescriptor)
    for ((callPredicate, value) in methodCalls) {
        val call = callPredicate.call as CallTerm
        val mock = termToDescriptor[call.owner]
        if (mock is MockDescriptor) {
            mock.addReturnValue(call.method, value)
        }
    }
    return withMocks
}