package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.StaticFieldGetter
import org.jetbrains.research.kex.reanimator.callstack.UnknownCall
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.util.loadKClass
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

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        val name = descriptor.term.toString()
        val cs = CallStack(name)
        saveToCache(descriptor, cs)

        val kfgType = descriptor.type.getKfgType(types) as? ClassType
            ?: return cs.also { it += UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name) }
        val kClass = tryOrNull { loader.loadKClass(kfgType) }
            ?: return cs.also { it += UnknownCall(kfgType, descriptor).wrap(name) }

        if (kClass.isCompanion) {
            val (parentClass, companionName) = kfgType.`class`.fullname.split("\$")
            val kfgParent = cm[parentClass]
            cs += StaticFieldGetter(kfgParent, companionName)
        } else if (kClass.objectInstance != null) {
            cs += StaticFieldGetter(kfgType.`class`, "INSTANCE")
        }
        return cs
    }
}
