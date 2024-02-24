package org.vorpal.research.kex.parameters

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kex.util.MockingMode
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kex.util.mockingMode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.logging.debug
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
    types: TypeFactory
): Map<Descriptor, Descriptor> {
    val mapped = mutableMapOf<Descriptor, Descriptor>()
    allDescriptors.forEach { it.transform(mapped) { it.replaceWithMock(types) } }
    return mapped
}

fun Descriptor.isMockable(types: TypeFactory): Boolean {
    val klass = (type.getKfgType(types) as? ClassType)?.klass ?: return false
    val necessaryConditions = !klass.isFinal && !type.isKexRt && this is ObjectDescriptor
    return necessaryConditions && when (kexConfig.mockingMode) {
        MockingMode.FULL -> true
        MockingMode.BASIC -> !instantiationManager.isInstantiable(klass)
        null -> false
    }
}

fun Descriptor.requireMocks(
    types: TypeFactory, visited: MutableSet<Descriptor> = mutableSetOf()
): Boolean = any(visited) { descriptor -> descriptor.isMockable(types) }


// TODO, pass predicate in argument instead of TypeFactory
fun Descriptor.replaceWithMock(types: TypeFactory): Descriptor {
    if (!this.isMockable(types)) {
        return this
    }
    return MockDescriptor(this as ObjectDescriptor).apply {
        fields.putAll(this@replaceWithMock.fields)
        log.debug { "Created mock descriptor for $term" }
    }
}


fun Class.getFunctionalInterfaces(
    ctx: ExecutionContext,
    acc: MutableSet<Class> = mutableSetOf()
): Set<Class> {
    if (this.isInterface &&
        ctx.loader.loadClass(this).getAnnotation(FunctionalInterface::class.java) != null
    ) {
        acc.add(this)
        return acc
    }
    allAncestors.forEach { it.getFunctionalInterfaces(ctx, acc) }
    return acc
}


fun Descriptor.fixConcreteLambdas(
    ctx: ExecutionContext,
    visited: MutableMap<Descriptor, Descriptor> = mutableMapOf()
): Descriptor {
    return this.transform(visited) {
        if (this !is ObjectDescriptor) {
            return@transform this
        }
        val clazz = this.type as KexClass
        val kfgClass = clazz.kfgClass(ctx.types)
        val functionalInterfaces = kfgClass.getFunctionalInterfaces(ctx)
        when (functionalInterfaces.size) {
            0 -> this
            1 -> descriptor { `object`(functionalInterfaces.first().kexType) }
            // TODO create mock with all interfaces
            else -> descriptor { `object`(functionalInterfaces.random().kexType) }.also {
                log.warn { "FIXME: Multiple functional interfaces, so chosen random one :D" }
            }
        }
    }
}

fun setupMocks(
    methodCalls: List<CallPredicate>,
    termToDescriptor: Map<Term, Descriptor>,
    descriptorToMock: Map<Descriptor, Descriptor>
) {
    for (callPredicate in methodCalls) {
        if (!callPredicate.hasLhv) continue
        val call = callPredicate.call as CallTerm
        if (call.method.name == "getClass") continue

        val mock =
            termToDescriptor[call.owner]?.let { descriptorToMock[it] ?: it } as? MockDescriptor
        val value = termToDescriptor[callPredicate.lhvUnsafe]?.let { descriptorToMock[it] ?: it }
        mock ?: log.warn { "No mock for $call" }

        if (mock is MockDescriptor && value != null) {
            mock.addReturnValue(call.method, value)
        }
    }
}