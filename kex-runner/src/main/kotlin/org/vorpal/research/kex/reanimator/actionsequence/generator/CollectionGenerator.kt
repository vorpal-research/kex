package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.type
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.CodeAction
import org.vorpal.research.kex.reanimator.actionsequence.MethodCall
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method

// difference from any generator -- ignore all external and recursive ctors
class CollectionGenerator(fallback: Generator) : AnyGenerator(fallback) {
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
        return klass.getKfgType(tf).isSubtypeOf(tf.cm["java/util/Collection"].type)
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
    ): List<GeneratorContext.ExecutionStack<ObjectDescriptor>> = with(context) {
        val stackList = mutableListOf<GeneratorContext.ExecutionStack<ObjectDescriptor>>()
        val acceptExecResult = { method: Method, res: Parameters<Descriptor>, oldDepth: Int ->
            val (result, args) = res
            if (result != null && result neq current) {
                val remapping = { mutableMapOf<Descriptor, Descriptor>(result to current) }
                val generatedArgs = generateArgs(args.map { it.deepCopy(remapping()) }, generationDepth + 1)
                if (generatedArgs != null) {
                    val newStack = currentStack + MethodCall(method, generatedArgs)
                    val newDesc = (result as ObjectDescriptor).merge(current)
                    stackList += GeneratorContext.ExecutionStack(newDesc, newStack, oldDepth + 1)
                }
            }
        }

        val collectionKlass = cm["java/util/Collection"].type
        val accessibleMethods = klass.accessibleMethods
            .filter { it.name !in ignoredMethods }
            .filterNot {
                it.argTypes.any { arg -> arg.isSubtypeOf(collectionKlass) }
            }
        for (method in accessibleMethods) {
            method.executeAsSetter(current)?.let {
                acceptExecResult(method, it, searchDepth)
            }
            method.executeAsMethod(current)?.let {
                acceptExecResult(method, it, searchDepth)
            }
        }
        return stackList
    }
}