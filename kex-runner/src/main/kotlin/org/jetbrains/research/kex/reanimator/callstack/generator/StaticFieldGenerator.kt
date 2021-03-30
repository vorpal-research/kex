package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.collector.hasSetter
import org.jetbrains.research.kex.reanimator.collector.setter
import org.jetbrains.research.kex.reanimator.descriptor.ClassDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log

private val maxStackSize by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 5) }
private val maxQuerySize by lazy { kexConfig.getIntValue("apiGeneration", "maxQuerySize", 1000) }

class StaticFieldGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor is ClassDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? ClassDescriptor ?: throw IllegalArgumentException()
        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        saveToCache(descriptor, callStack)
        generateStatics(callStack, descriptor, generationDepth)
        return callStack
    }

    data class ExecutionStack(val instance: ClassDescriptor, val calls: List<ApiCall>, val depth: Int)

    private fun generateStatics(callStack: CallStack, descriptor: ClassDescriptor, generationDepth: Int) = with(context) {
        val original = descriptor.deepCopy() as ClassDescriptor
        val fallbacks = mutableSetOf<List<ApiCall>>()

        descriptor.concretize(cm)
        descriptor.filterFinalFields(cm)
        descriptor.reduce()
        if (descriptor.isFinal(original)) return

        log.debug("Generating $descriptor")

        val klass = descriptor.klass.kfgClass(types)
        val setters = descriptor.generateSetters(generationDepth)
        val queue = queueOf(ExecutionStack(descriptor, setters, 0))
        while (queue.isNotEmpty()) {
            val es = queue.poll()

            if (queue.size > maxQuerySize) {
                break
            }

            val (desc, stack, depth) = es
            val current = descriptor.accept(desc)
            current.reduce()
            if (depth > maxStackSize) continue
            log.debug("Depth $generationDepth, stack depth $depth, query size ${queue.size}")

            for (method in klass.staticMethods) {
                val executionResults = listOfNotNull(
                    method.executeAsStaticSetter(current),
                    method.executeAsStaticMethod(current)
                )

                for ((instance, args, statics) in executionResults) {
                    ktassert(instance == null) { log.error("`this` descriptor is not null in static") }

                    val resultingDescriptor = statics.firstOrNull { it.type == current.type } as? ClassDescriptor
                    val generatedArgs = generateArgs(args, generationDepth) ?: continue
                    val apiCall = StaticMethodCall(method, generatedArgs)

                    if (resultingDescriptor.isFinal(current)) {
                        val result = (stack + apiCall).reversed()
                        if (result.isComplete) {
                            callStack.stack += (stack + apiCall).reversed()
                            return
                        } else {
                            fallbacks += result
                        }
                    } else if (resultingDescriptor!! neq current) {
                        val newStack = stack + apiCall
                        val newDesc = resultingDescriptor.merge(current)
                        queue += ExecutionStack(newDesc, newStack, depth + 1)
                    }
                }
            }
        }

        if (fallbacks.isNotEmpty()) {
            callStack.stack.clear()
            callStack.stack += fallbacks.random()
        } else {
            callStack += UnknownCall(klass.type, original)
        }
    }

    private fun generateArgs(args: List<Descriptor>, depth: Int): List<CallStack>? = try {
        args.map { fallback.generate(it, depth) }
    } catch (e: SearchLimitExceededException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private fun ClassDescriptor.generateSetters(generationDepth: Int): List<ApiCall> = with(context) {
        val calls = mutableListOf<ApiCall>()
        val kfgKlass = klass.kfgClass(types)
        for ((field, value) in fields.toMap()) {
            val kfgField = kfgKlass.getField(field.first, field.second.getKfgType(types))

            if (visibilityLevel <= kfgField.visibility) {
                log.debug("Directly setting field $field value")
                calls += StaticFieldSetter(kfgKlass, kfgField, fallback.generate(value, generationDepth + 1))
                fields.remove(field)
                reduce()

            } else if (kfgField.hasSetter && visibilityLevel <= kfgField.setter.visibility) {
                log.info("Using setter for $field")

                val (instance, args, statics) = kfgField.setter.executeAsStaticSetter(this@generateSetters) ?: continue
                ktassert(instance == null) { log.error("`this` descriptor is not null in static") }

                val resultingDescriptor = statics.firstOrNull { it.type == klass } as? ClassDescriptor

                if (resultingDescriptor == null || field notIn resultingDescriptor) {
                    val generatedArgs = generateArgs(args, generationDepth + 1) ?: continue
                    calls += StaticMethodCall(kfgField.setter, generatedArgs)
                    accept(resultingDescriptor ?: ClassDescriptor(klass))
                    reduce()
                    log.info("Used setter for field $field, new desc: $this")
                }
            }
        }
        return calls
    }
}