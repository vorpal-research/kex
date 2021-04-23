package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.descriptor
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log

class InnerClassGenerator(fallback: Generator) : AnyGenerator(fallback) {

    val KexClass.isInnerClass: Boolean
        get() {
            val kfgClass = context.cm[klass] as? ConcreteClass ?: return false
            return kfgClass.outerClass != null && kfgClass.fields.any { it.name == "this\$0" && it.type == kfgClass.outerClass!!.type }
        }

    override fun supports(descriptor: Descriptor): Boolean {
        val type = descriptor.type
        val klass = type as? KexClass ?: return false
        return klass.isInnerClass
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack {
        descriptor as ObjectDescriptor
        val klass = (descriptor.type as KexClass).klass
        val kfgClass = context.cm[klass] as ConcreteClass
        val outerField = kfgClass.fields.first { it.name.startsWith("this\$") && it.type == kfgClass.outerClass!!.type }
        val fieldKey = outerField.name to outerField.type.kexType
        if (fieldKey !in descriptor.fields) {
            descriptor[fieldKey] = descriptor { `object`(kfgClass.outerClass!!.kexType) }
        }
        return super.generate(descriptor, generationDepth)
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
                for (method in klass.orderedCtors) {
                    val handler = when {
                        method.isConstructor -> { it: Method -> current.checkInnerCtor(it, generationDepth) }
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

    fun ObjectDescriptor.checkInnerCtor(method: Method, generationDepth: Int): ApiCall? =
            with(context) {
                val (thisDesc, args) = method.executeAsConstructor(this@checkInnerCtor) ?: return null

                if ((thisDesc as ObjectDescriptor).isFinal(this@checkInnerCtor)) {
                    log.debug("Found constructor $method for $this, generating arguments $args")
                    val generatedArgs = generateArgs(args, generationDepth + 1) ?: return null
                    ktassert(generatedArgs.size > 1) { log.error("Unknown number of arguments of inner class") }
                    InnerClassConstructorCall(method, generatedArgs.first(), generatedArgs.drop(1))
                } else null
            }

}