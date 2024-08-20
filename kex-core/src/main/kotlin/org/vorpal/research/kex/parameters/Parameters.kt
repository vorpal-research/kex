package org.vorpal.research.kex.parameters

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
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

// `instance` is always nullable, so `transform` may or may not accept null
inline fun <T : F, U, reified F> Parameters<T>.map(transform: (F) -> U): Parameters<U> {
    return Parameters(
        if (instance is F) transform(instance) else null,
        arguments.map(transform),
        statics.mapTo(mutableSetOf(), transform)
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

fun Parameters<Descriptor>.replaceNullsWithDefaultValues(method: Method, cm: ClassManager): Parameters<Descriptor> {
    val parametersWithoutNulls = arguments
        .zip(method.argTypes)
        .map { (descriptor, type) -> transformExistingDescriptor(descriptor, type.kexType, cm) }
    return Parameters(instance, parametersWithoutNulls, statics)
}

private fun transformExistingDescriptor(descriptor: Descriptor, type: KexType, cm: ClassManager, alreadyCopied: MutableMap<Descriptor, Descriptor> = mutableMapOf()): Descriptor {
    if (descriptor in alreadyCopied) return alreadyCopied[descriptor]!!
    return when (descriptor) {
        is ConstantDescriptor.Null -> type.newInstance(cm).also { alreadyCopied[descriptor] = it }
        is ConstantDescriptor -> descriptor.also { alreadyCopied[descriptor] = it }
        is ArrayDescriptor -> ArrayDescriptor(descriptor.elementType, descriptor.length)
            .apply {
                alreadyCopied[descriptor] = this
                (0 until descriptor.length).forEach { index ->
                    this[index] = transformExistingDescriptor(
                        descriptor[index] ?: ConstantDescriptor.Null,
                        descriptor.elementType,
                        cm,
                        alreadyCopied
                    )
                }
            }

        is ObjectDescriptor -> ObjectDescriptor(descriptor.klass).apply {
            alreadyCopied[descriptor] = this
            descriptor.fields.forEach { (key, desc) ->
                val (name, fieldType) = key
                set(name to fieldType, transformExistingDescriptor(desc, fieldType, cm, alreadyCopied))
            }
        }

        is ClassDescriptor -> ClassDescriptor(descriptor.klass).apply {
            alreadyCopied[descriptor] = this
            descriptor.fields.forEach { (key, desc) ->
                val (name, fieldType) = key
                set(name to fieldType, transformExistingDescriptor(desc, fieldType, cm, alreadyCopied))
            }
        }

        is MockDescriptor -> MockDescriptor(descriptor.klass).apply {
            alreadyCopied[descriptor] = this
            descriptor.fields.forEach { (key, desc) ->
                val (name, fieldType) = key
                set(name to fieldType, transformExistingDescriptor(desc, fieldType, cm, alreadyCopied))
            }
        }
    }
}

private fun KexType.newInstance(cm: ClassManager): Descriptor {
    val descriptor = descriptor { default(this@newInstance, nullable = false) }
    if (descriptor !is ClassDescriptor) return descriptor
    val kfgClass = (this as KexClass).kfgClass(cm.type)
    kfgClass
        .fields
        .filterNot { it.isStatic }
        .forEach {
            if (!it.isStatic)
                descriptor[it.name to this] = it.type.kexType.newInstance(cm)
        }
    return descriptor
}
