package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.ClassDescriptor
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.actionsequence.*
import org.jetbrains.research.kex.reanimator.collector.hasSetter
import org.jetbrains.research.kex.reanimator.collector.setter
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log

class StaticFieldGenerator(private val fallback: Generator) : Generator {
    private val maxStackSize by lazy { kexConfig.getIntValue("reanimator", "maxStackSize", 5) }
    private val maxQuerySize by lazy { kexConfig.getIntValue("reanimator", "maxQuerySize", 1000) }

    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor is ClassDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ClassDescriptor ?: throw IllegalArgumentException()
        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)
        return if (generateStatics(actionSequence, descriptor, generationDepth)) actionSequence
        else UnknownSequence(name, descriptor.type.getKfgType(types), descriptor).also {
            saveToCache(descriptor, it)
        }
    }

    data class ExecutionStack(val instance: ClassDescriptor, val calls: List<CodeAction>, val depth: Int)

    private fun generateStatics(actionSequence: ActionList, descriptor: ClassDescriptor, generationDepth: Int): Boolean = with(context) {
        val original = descriptor.deepCopy() as ClassDescriptor
        val fallbacks = mutableSetOf<List<CodeAction>>()

        descriptor.concretize(cm)
        descriptor.filterFinalFields(cm)
        descriptor.reduce()
        if (descriptor.isFinal(original)) return true

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
                            actionSequence += (stack + apiCall).reversed()
                            return true
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

        return if (fallbacks.isNotEmpty()) {
            actionSequence.clear()
            actionSequence += fallbacks.random()
            true
        } else false
    }

    private fun generateArgs(args: List<Descriptor>, depth: Int): List<ActionSequence>? = try {
        args.map { fallback.generate(it, depth) }
    } catch (e: SearchLimitExceededException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private fun ClassDescriptor.generateSetters(generationDepth: Int): List<CodeAction> = with(context) {
        val calls = mutableListOf<CodeAction>()
        val kfgKlass = klass.kfgClass(types)
        for ((field, value) in fields.toMap()) {
            val kfgField = kfgKlass.getField(field.first, field.second.getKfgType(types))

            if (visibilityLevel <= kfgField.visibility) {
                log.debug("Directly setting field $field value")
                calls += StaticFieldSetter(kfgField, fallback.generate(value, generationDepth + 1))
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