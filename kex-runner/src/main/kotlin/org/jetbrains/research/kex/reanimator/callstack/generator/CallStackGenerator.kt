package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.descriptor.ConstantDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.StaticFieldDescriptor
import org.jetbrains.research.kthelper.KtException
import org.jetbrains.research.kthelper.logging.debug
import org.jetbrains.research.kthelper.logging.log

private val maxGenerationDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxGenerationDepth", 100) }
private val maxSearchDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxSearchDepth", 10000) }

class SearchLimitExceededException(val descriptor: Descriptor, msg: String) : KtException(msg)

class CallStackGenerator(override val context: GeneratorContext) : Generator {
    private val anyGenerator = AnyGenerator(this)
    private val arrayGenerator = ArrayGenerator(this)
    private val typeGenerators = mutableSetOf<Generator>()
    private var searchDepth = 0

    override fun supports(type: KexType) = true

    init {
        typeGenerators += StringGenerator(this)
        typeGenerators += EnumGenerator(this)
        typeGenerators += KtObjectGenerator(this)
        typeGenerators += InnerClassGenerator(this)
    }

    constructor(executionCtx: ExecutionContext, psa: PredicateStateAnalysis, visibilityLevel: Visibility)
            : this(GeneratorContext(executionCtx, psa, visibilityLevel))

    val KexType.generator: Generator
        get() = when (this) {
            is KexArray -> arrayGenerator
            else -> typeGenerators.firstOrNull { it.supports(this) } ?: anyGenerator
        }

    fun generateDescriptor(descriptor: Descriptor): CallStack {
        searchDepth = 0
        val originalDescriptor = descriptor.deepCopy()
        return try {
            generate(descriptor)
        } catch (e: SearchLimitExceededException) {
            val name = "${originalDescriptor.term}"
            log.debug("Search limit exceeded: ${e.message}")
            log.debug(originalDescriptor)
            return UnknownCall(originalDescriptor.type.getKfgType(context.types), originalDescriptor).wrap(name)
        }
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor.cached()?.let { return it }
        searchDepth++

        val name = "${descriptor.term}"

        if (descriptor !is ConstantDescriptor) {
            if (generationDepth > maxGenerationDepth)
                throw SearchLimitExceededException(descriptor, "Generation depth exceeded maximal limit $maxGenerationDepth")
            //return UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name)
            if (searchDepth > maxSearchDepth)
                throw SearchLimitExceededException(descriptor, "Search depth exceeded maximal limit $maxSearchDepth")
        //return UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name)
        }

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
            }
            is StaticFieldDescriptor -> {
                val callStack = CallStack(name)
                descriptor.cache(callStack)
                val kfgClass = descriptor.klass.kfgClass(types)
                val kfgField = kfgClass.getField(descriptor.field, descriptor.type.getKfgType(types))
                if (visibilityLevel <= kfgField.visibility) {
                    callStack += StaticFieldSetter(kfgClass, kfgField, generate(descriptor.value, generationDepth + 1))
                }
            }
            else -> {
                val typeGenerator = descriptor.type.generator
                typeGenerator.generate(descriptor, generationDepth + 1)
            }
        }
        return descriptor.cached()!!
    }
}
