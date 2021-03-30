package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.callstack.ApiCall
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.MethodCall
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.ObjectDescriptor
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method

// difference from any generator -- ignore all external and recursive ctors
class CollectionGenerator(fallback: Generator) : AnyGenerator(fallback) {

    override fun supports(descriptor: Descriptor): Boolean {
        val type = descriptor.type
        val klass = type as? KexClass ?: return false
        val tf = context.types
        return klass.getKfgType(tf).isSubtypeOf(tf.cm["java/util/Collection"].type)
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
        for (method in klass.accessibleMethods.filterNot {
            it.argTypes.any { arg -> arg.isSubtypeOf(collectionKlass) }
        }) {
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