package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.CodeAction
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kfg.collectionClass
import org.vorpal.research.kfg.ir.Class

// difference from any generator -- ignore all external and recursive ctors
class ReanimatingCollectionGenerator(fallback: Generator) : AnyGenerator(fallback) {
    companion object {
        private val ignoredMethods = setOf(
            "trimToSize",
            "addAll",
            "size",
            "ensureCapacity",
            "isEmpty",
            "contains",
            "indexOf",
            "lastIndexOf",
            "clone",
            "toArray"
        )
    }

    override fun supports(descriptor: Descriptor): Boolean {
        val type = descriptor.type
        val klass = type as? KexClass ?: return false
        val tf = context.types
        return klass.getKfgType(tf).isSubtypeOfCached(tf.cm.collectionClass.asType)
    }

    override fun checkCtors(
        sequence: ActionList,
        klass: Class,
        current: ObjectDescriptor,
        currentStack: List<CodeAction>,
        fallbacks: MutableSet<List<CodeAction>>,
        generationDepth: Int
    ): Boolean = internalCheckConstructors(
        sequence, klass, current, currentStack, fallbacks, generationDepth
    ) { with(context) { klass.nonRecursiveCtors } }

    override fun applyMethods(
        sequence: ActionList,
        klass: Class,
        current: ObjectDescriptor,
        currentStack: List<CodeAction>,
        searchDepth: Int,
        generationDepth: Int
    ): List<GeneratorContext.ExecutionStack<ObjectDescriptor>> = internalApplyMethods(
        current, currentStack, searchDepth, generationDepth
    ) {
        with(context) {
            val collectionKlass = cm.collectionClass.asType
            klass.accessibleMethods
                .filter { it.name !in ignoredMethods }
                .filterNotTo(mutableSetOf()) {
                    it.argTypes.any { arg -> arg.isSubtypeOfCached(collectionKlass) }
                }
        }
    }
}
