package org.jetbrains.research.kex.generator.callstack

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.generator.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.generator.descriptor.Descriptor
import org.jetbrains.research.kex.generator.descriptor.StaticFieldDescriptor
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType

private val maxGenerationDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxGenerationDepth", 100) }

class CallStackGenerator(executionCtx: ExecutionContext, psa: PredicateStateAnalysis) : CSGenerator {
    override val context = GeneratorContext(executionCtx, psa)
    private val anyGenerator = AnyGenerator(this)
    private val arrayGenerator = ArrayGenerator(this)
    private val typeGenerators = mutableMapOf<KexType, CSGenerator>()

    override fun supports(type: KexType) = true

    init {
        typeGenerators += KexClass("java/lang/String") to StringGenerator(this)
    }

    val KexType.generator: CSGenerator
        get() = when (this) {
            in typeGenerators -> typeGenerators.getValue(this)
            is KexArray -> arrayGenerator
            else -> anyGenerator
        }

    override fun generate(descriptor: Descriptor, depth: Int): CallStack = with(context) {
        descriptor.cached()?.let { return it }

        val name = "${descriptor.term}"
        if (depth > maxGenerationDepth) return UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name)

        when (descriptor) {
            is ConstantDescriptor -> return when (descriptor) {
                is ConstantDescriptor.Null -> PrimaryValue(null)
                is ConstantDescriptor.Bool -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Byte -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Char -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Short -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Int -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Long -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Float -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Double -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Class -> PrimaryValue(descriptor.value)
            }.wrap(name)
            is StaticFieldDescriptor -> {
                val callStack = CallStack(name)
                descriptor.cache(callStack)
                val kfgClass = descriptor.klass.kfgClass(types)
                val kfgField = kfgClass.getField(descriptor.field, descriptor.type.getKfgType(types))
                val typeGenerator = descriptor.value.type.generator
                callStack += StaticFieldSetter(kfgClass, kfgField, typeGenerator.generate(descriptor.value, depth + 1))
            }
            else -> {
                val typeGenerator = descriptor.type.generator
                typeGenerator.generate(descriptor, depth)
            }
        }
        return descriptor.cached()!!
    }
}