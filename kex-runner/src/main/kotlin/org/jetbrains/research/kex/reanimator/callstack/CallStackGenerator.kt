package org.jetbrains.research.kex.reanimator.callstack

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.reanimator.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.StaticFieldDescriptor

private val maxGenerationDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxGenerationDepth", 100) }
private val maxSearchDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxSearchDepth", 10000) }

class CallStackGenerator(executionCtx: ExecutionContext, psa: PredicateStateAnalysis) : Generator {
    override val context = GeneratorContext(executionCtx, psa)
    private val anyGenerator = AnyGenerator(this)
    private val arrayGenerator = ArrayGenerator(this)
    private val typeGenerators = mutableMapOf<KexType, Generator>()
    private var searchDepth = 0

    override fun supports(type: KexType) = true

    init {
        typeGenerators += KexClass("java/lang/String") to StringGenerator(this)
    }

    val KexType.generator: Generator
        get() = when (this) {
            in typeGenerators -> typeGenerators.getValue(this)
            is KexArray -> arrayGenerator
            else -> anyGenerator
        }

    fun generateDescriptor(descriptor: Descriptor): CallStack {
        searchDepth = 0
        return generate(descriptor)
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor.cached()?.let { return it }
        searchDepth++

        val name = "${descriptor.term}"
        if (generationDepth > maxGenerationDepth) return UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name)
        if (searchDepth > maxSearchDepth) return UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name)

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
                callStack += StaticFieldSetter(kfgClass, kfgField, typeGenerator.generate(descriptor.value, generationDepth + 1))
            }
            else -> {
                val typeGenerator = descriptor.type.generator
                typeGenerator.generate(descriptor, generationDepth + 1)
            }
        }
        return descriptor.cached()!!
    }
}