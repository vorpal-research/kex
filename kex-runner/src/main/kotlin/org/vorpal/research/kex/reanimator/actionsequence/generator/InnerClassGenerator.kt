package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.CodeAction
import org.vorpal.research.kex.reanimator.actionsequence.InnerClassConstructorCall
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log

class InnerClassGenerator(fallback: Generator) : AnyGenerator(fallback) {

    private val KexClass.isInnerClass: Boolean
        get() {
            val kfgClass = context.cm[klass] as? ConcreteClass ?: return false
            return kfgClass.outerClass != null && kfgClass.fields.any {
                it.name == "this\$0" && it.type == kfgClass.outerClass!!.asType
            }
        }

    override fun supports(descriptor: Descriptor): Boolean {
        val type = descriptor.type
        val klass = type as? KexClass ?: return false
        return klass.isInnerClass && descriptor is ObjectDescriptor
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence {
        descriptor as ObjectDescriptor
        val klass = (descriptor.type as KexClass).klass
        val kfgClass = context.cm[klass] as ConcreteClass
        val outerField = kfgClass.fields.first {
            it.name.startsWith("this\$") && it.type == kfgClass.outerClass!!.asType
        }
        val fieldKey = outerField.name to outerField.type.kexType
        if (fieldKey !in descriptor.fields) {
            descriptor[fieldKey] = descriptor { `object`(kfgClass.outerClass!!.kexType) }
        }
        return super.generate(descriptor, generationDepth)
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
            for (method in klass.orderedCtors) {
                val handler = when {
                    method.isConstructor -> { it: Method -> current.checkInnerCtor(it, generationDepth) }
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

    private fun ObjectDescriptor.checkInnerCtor(method: Method, generationDepth: Int): CodeAction? =
        with(context) {
            val (thisDesc, args) = method.executeAsConstructor(this@checkInnerCtor) ?: return null

            if ((thisDesc as ObjectDescriptor).isFinal(this@checkInnerCtor)) {
                log.debug("Found constructor {} for {}, generating arguments {}", method, this, args)
                val generatedArgs = generateArgs(args, generationDepth + 1) ?: return null
                ktassert(generatedArgs.size > 1) { log.error("Unknown number of arguments of inner class") }
                InnerClassConstructorCall(method, generatedArgs.first(), generatedArgs.drop(1))
            } else null
        }

}
