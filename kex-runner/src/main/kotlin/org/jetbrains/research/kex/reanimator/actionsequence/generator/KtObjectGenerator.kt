package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.actionsequence.ActionList
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kex.reanimator.actionsequence.StaticFieldGetter
import org.jetbrains.research.kex.reanimator.actionsequence.UnknownSequence
import org.jetbrains.research.kex.util.loadKClass
import org.jetbrains.research.kex.util.splitAtLast
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.tryOrNull

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
