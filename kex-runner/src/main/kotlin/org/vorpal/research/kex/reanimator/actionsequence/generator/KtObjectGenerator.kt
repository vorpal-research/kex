package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.StaticFieldGetter
import org.vorpal.research.kex.reanimator.actionsequence.UnknownSequence
import org.vorpal.research.kex.util.loadKClass
import org.vorpal.research.kex.util.splitAtLast
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.tryOrNull

class KtObjectGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    val types get() = context.types
    val loader get() = context.loader

    override fun supports(descriptor: Descriptor): Boolean {
        val type = descriptor.type
        val kClass = tryOrNull { loader.loadKClass(types, type) } ?: return false
        return kClass.isCompanion || kClass.objectInstance != null
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        val name = descriptor.term.toString()
        val cs = ActionList(name)
        saveToCache(descriptor, cs)

        val kfgType = descriptor.type.getKfgType(types) as? ClassType
            ?: return UnknownSequence(name, descriptor.type.getKfgType(types), descriptor).also {
                saveToCache(descriptor, it)
            }
        val kClass = tryOrNull { loader.loadKClass(kfgType) }
            ?: return UnknownSequence(name, descriptor.type.getKfgType(types), descriptor).also {
                saveToCache(descriptor, it)
            }

        if (kClass.isCompanion) {
            val (parentClass, companionName) = kfgType.klass.fullName.splitAtLast('$')
            val kfgParent = cm[parentClass]
            cs += StaticFieldGetter(kfgParent.getField(companionName, descriptor.type.getKfgType(types)))
        } else if (kClass.objectInstance != null) {
            cs += StaticFieldGetter(kfgType.klass.getField("INSTANCE", kfgType))
        }
        return cs
    }
}
