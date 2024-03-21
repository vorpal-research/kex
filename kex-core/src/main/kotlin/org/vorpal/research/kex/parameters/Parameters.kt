package org.vorpal.research.kex.parameters

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.mocking.MockMaker
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn
import kotlin.random.Random

@Serializable
data class Parameters<T>(
    val instance: T?,
    val arguments: List<T>,
    val statics: Set<T> = setOf()
) {
    val asList get() = listOfNotNull(instance) + arguments + statics

    override fun toString(): String = buildString {
        appendLine("instance: $instance")
        if (arguments.isNotEmpty())
            appendLine("args: ${arguments.joinToString("\n")}")
        if (statics.isNotEmpty())
            appendLine("statics: ${statics.joinToString("\n")}")
    }
}

fun <T, U> Parameters<T>.map(transform: (T) -> U): Parameters<U> {
    return Parameters(
        this.instance?.let(transform),
        this.arguments.map(transform),
        this.statics.mapTo(mutableSetOf(), transform)
    )
}

val Parameters<Any?>.asDescriptors: Parameters<Descriptor>
    get() {
        val context = Object2DescriptorConverter()
        return Parameters(
            context.convert(instance),
            arguments.map { context.convert(it) },
            statics.mapTo(mutableSetOf()) { context.convert(it) },
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
    return Parameters(instance, arguments, filteredStatics)
}

private val ignoredStatics: Set<KfgTargetFilter> by lazy {
    kexConfig.getMultipleStringValue("testGen", "ignoreStatic").flatMapTo(mutableSetOf()) {
        val filter = KfgTargetFilter.parse(it)
        listOf(filter, filter.rtMapped)
    }
}

fun Parameters<Descriptor>.filterIgnoredStatic(): Parameters<Descriptor> {
    val filteredStatics = statics
        .filterIsInstance<ClassDescriptor>()
        .filterTo(mutableSetOf()) { descriptor ->
            val typeName = descriptor.type.toString()
            ignoredStatics.all { ignored ->
                !ignored.matches(typeName)
            }
        }
    return Parameters(instance, arguments, filteredStatics)
}


fun createDescriptorToMock(
    allDescriptors: Collection<Descriptor>,
    mockMaker: MockMaker,
    expectedClasses: Map<Descriptor, Class>
): Map<Descriptor, MockDescriptor> {
    val descriptorToMock = mutableMapOf<Descriptor, MockDescriptor>()
    allDescriptors.forEach {
        it.transform(descriptorToMock) { descriptor ->
            mockMaker.mockOrNull(descriptor, expectedClasses[descriptor])
        }
    }
    return descriptorToMock
}


fun Descriptor.requireMocks(
    mockMaker: MockMaker,
    expectedClass: Map<Descriptor, Class>,
    visited: MutableSet<Descriptor> = mutableSetOf()
): Boolean =
    any(visited) { descriptor -> mockMaker.canMock(descriptor, expectedClass[descriptor]) }


private fun Method.mockitoCanMock(types: TypeFactory): Boolean = when {
    name == "getClass" && argTypes.isEmpty() -> false
    name == "hashCode" && argTypes.isEmpty() -> false
    name == "equals" && argTypes == listOf(types.objectType) -> false
    isFinal || isPrivate -> false

    else -> true
}

fun setupMocks(
    types: TypeFactory,
    methodCalls: List<CallPredicate>,
    termToDescriptor: Map<Term, Descriptor>,
    descriptorToMock: Map<Descriptor, MockDescriptor>,
) {
    for (callPredicate in methodCalls) {
        if (!callPredicate.hasLhv) continue
        val call = callPredicate.call as CallTerm
        if (call.method.mockitoCanMock(types).not()) continue

        val mock =
            termToDescriptor[call.owner]?.let { descriptorToMock[it] ?: it } as? MockDescriptor
        val value = termToDescriptor[callPredicate.lhvUnsafe]?.let { descriptorToMock[it] ?: it }
        mock ?: log.warn { "No mock for $call" }

        if (mock is MockDescriptor && value != null) {
            mock.addReturnValue(call.method, value)
        }
    }
}