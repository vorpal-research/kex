package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.callstack.ApiCall
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.ObjectDescriptor
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method

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
        callStack: CallStack,
        klass: Class,
        current: ObjectDescriptor,
        currentStack: List<ApiCall>,
        fallbacks: MutableSet<List<ApiCall>>,
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
                    callStack.stack += (currentStack + apiCall).reversed()
                    return true
                } else {
                    fallbacks += result
                }
            }
            return false
        }

    override fun applyMethods(
        klass: Class,
        current: ObjectDescriptor,
        currentStack: List<ApiCall>,
        searchDepth: Int,
        generationDepth: Int
    ): List<GeneratorContext.ExecutionStack<ObjectDescriptor>> = emptyList()
}