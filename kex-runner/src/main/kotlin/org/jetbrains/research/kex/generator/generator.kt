package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.logging.debug
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val apiGeneration get() = kexConfig.getBooleanValue("apiGeneration", "enabled", false)

class Generator(val ctx: ExecutionContext, val psa: PredicateStateAnalysis) {
    val cm: ClassManager get() = ctx.cm
    val csGenerator = CallStackGenerator(ctx, psa)
    val csExecutor = CallStackExecutor(ctx)

    private val Class.isInstantiable: Boolean
        get() = when {
            this.isAbstract -> false
            this.isInterface -> false
            else -> true
        }

    private val Descriptor.concrete
        get() = when (this) {
            is ObjectDescriptor -> this.instantiableDescriptor
            else -> this
        }

    private val ObjectDescriptor.instantiableDescriptor: ObjectDescriptor
        get() {
            val concreteClass = when {
                this.klass.isInstantiable -> this.klass
                else -> cm.concreteClasses.filter {
                    klass.isAncestorOf(it) && it.isInstantiable && visibilityLevel <= it.visibility
                }.random()
            }
            val result = ObjectDescriptor(klass = concreteClass)
            for ((name, desc) in this.fields) {
                result[name] = desc.copy(owner = result, value = desc.value.concrete)
            }
            return result
        }

    private val Pair<Descriptor?, List<Descriptor>>.concrete
        get() =
            first?.concrete to second.map { it.concrete }


    fun generateFromAPI(method: Method, state: PredicateState, model: SMTModel) = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concrete
        log.debug("Generated descriptors:")
        log.debug(descriptors)
        val thisCallStack = descriptors.first?.let { descriptor ->
            csGenerator.generate(descriptor)
        }
        val argCallStacks = descriptors.second.map { descriptor ->
            csGenerator.generate(descriptor)
        }
        thisCallStack?.let { csExecutor.execute(it) } to argCallStacks.map { csExecutor.execute(it) }.toTypedArray()
    } catch (e: Exception) {
        throw GenerationException(e)
    }

    fun generateFromModel(method: Method, state: PredicateState, model: SMTModel) =
            generateInputByModel(ctx, method, state, model)

    fun generate(method: Method, state: PredicateState, model: SMTModel) = when {
        apiGeneration -> generateFromAPI(method, state, model)
        else -> generateFromModel(method, state, model)
    }
}