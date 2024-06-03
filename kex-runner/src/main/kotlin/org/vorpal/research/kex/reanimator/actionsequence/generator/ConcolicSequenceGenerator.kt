package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.descriptor.ClassDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.FieldContainingDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionCall
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionGetField
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionGetStaticField
import org.vorpal.research.kex.reanimator.actionsequence.ReflectionList
import org.vorpal.research.kex.reanimator.actionsequence.generator.kexrt.KexRtGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.CharsetGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.ClassGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.CollectionGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.FieldGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.rt.StringGenerator
import org.vorpal.research.kex.util.getFieldSafe
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.logging.log

class ConcolicSequenceGenerator(override val context: GeneratorContext) : Generator {
    private val typeGenerators = mutableSetOf<Generator>()
    private var searchDepth = 0

    override fun supports(descriptor: Descriptor) = true


    init {
        typeGenerators += ConstantGenerator(context)
        typeGenerators += CharsetGenerator(this)
        typeGenerators += StringGenerator(this)
        typeGenerators += ClassGenerator(this)
        typeGenerators += MethodGenerator(this)
        typeGenerators += FieldGenerator(this)
        typeGenerators += ReflectionEnumGenerator(this)
        typeGenerators += CollectionGenerator(this)
        typeGenerators += KexRtGenerator(this)
        typeGenerators += MockGenerator(this)
        typeGenerators += UnknownGenerator(this)
    }

    constructor(executionCtx: ExecutionContext, psa: PredicateStateAnalysis)
            : this(GeneratorContext(executionCtx, psa))

    val Descriptor.generator: Generator
        get() = typeGenerators.firstOrNull { it.supports(this) } ?: unreachable {
            log.error("Could not find a generator for $this")
        }

    fun initializeStaticFinals(statics: Set<Descriptor>) = with(context) {
        val queue = queueOf<Triple<Field, ActionSequence, Descriptor>>()

        val processDescriptor = { descriptor: Descriptor, constructor: () -> ReflectionCall ->
            val actionSequence = ReflectionList("${descriptor.term}")
            actionSequence += constructor()
            saveToCache(descriptor, actionSequence)

            if (descriptor is FieldContainingDescriptor<*>) {
                val valueClass = (descriptor.type as KexClass).kfgClass(cm.type)
                for ((valueKey, valueValue) in descriptor.fields) {
                    val valueField = valueClass.getFieldSafe(valueKey.first, valueKey.second.getKfgType(cm.type))
                        ?: continue
                    queue += Triple(valueField, actionSequence, valueValue)
                }
            }
        }


        statics.filterIsInstance<ClassDescriptor>().forEach { klass ->
            val kfgClass = (klass.type as KexClass).kfgClass(cm.type)
            for ((key, descriptor) in klass.fields) {
                if (getFromCache(descriptor) != null) continue

                val field = kfgClass.getFieldSafe(key.first, key.second.getKfgType(cm.type)) ?: continue
                if (field.isFinal) {
                    processDescriptor(descriptor) { ReflectionGetStaticField(field) }
                }
            }
        }

        while (queue.isNotEmpty()) {
            val (field, owner, descriptor) = queue.poll()
            if (getFromCache(descriptor) != null) continue
            processDescriptor(descriptor) { ReflectionGetField(field, owner) }
        }
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        getFromCache(descriptor)?.let { return it }
        searchDepth++

        val typeGenerator = descriptor.generator

        return typeGenerator.generate(descriptor, generationDepth + 1)
    }
}
