package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.type
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.CodeAction
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method

// difference from any generator -- ignore all external and recursive ctors
// and ignore all methods
class MapGenerator(fallback: Generator) : AnyGenerator(fallback) {

    override fun supports(descriptor: Descriptor): Boolean {
        val type = descriptor.type
        val klass = type as? KexClass ?: return false
        val tf = context.types
        return klass.getKfgType(tf).isSubtypeOf(tf.cm["java/util/Map"].type)
    }

    override fun checkCtors(
        sequence: ActionList,
        klass: Class,
        current: ObjectDescriptor,
        currentStack: List<CodeAction>,
        fallbacks: MutableSet<List<CodeAction>>,
        generationDepth: Int
    ): Boolean =
        with(context) {
            for (method in klass.nonRecursiveCtors) {
                val handler = when {
                    method.isConstructor -> { it: Method -> current.checkCtor(klass, it, generationDepth) }
                    else -> { it: Method -> current.checkExternalCtor(it, generationDepth) }
                }
                val apiCall = handler(method) ?: continue
                val result = (currentStack + apiCall).reversed()
                if (result.isComplete) {
                    sequence += (currentStack + apiCall).reversed()
                    return true
                } else {
                    fallbacks += result
                }
            }
            return false
        }

    override fun applyMethods(
        sequence: ActionList,
        klass: Class,
        current: ObjectDescriptor,
        currentStack: List<CodeAction>,
        searchDepth: Int,
        generationDepth: Int
    ): List<GeneratorContext.ExecutionStack<ObjectDescriptor>> = emptyList()
}