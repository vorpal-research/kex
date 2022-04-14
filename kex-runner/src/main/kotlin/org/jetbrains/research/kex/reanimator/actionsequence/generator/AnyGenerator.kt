package org.jetbrains.research.kex.reanimator.actionsequence.generator

import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.actionsequence.*
import org.jetbrains.research.kex.reanimator.collector.hasSetter
import org.jetbrains.research.kex.reanimator.collector.setter
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log

open class AnyGenerator(private val fallback: Generator) : Generator {
    private val maxStackSize by lazy { kexConfig.getIntValue("reanimator", "maxStackSize", 5) }
    private val useSetters by lazy { kexConfig.getBooleanValue("reanimator", "useSetters", true) }
    private val maxQuerySize by lazy { kexConfig.getIntValue("reanimator", "maxQuerySize", 1000) }

    override val context get() = fallback.context

    override fun supports(descriptor: Descriptor) = descriptor is ObjectDescriptor

    override fun generate(descriptor: Descriptor, generationDepth: Int): ActionSequence = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()

        val name = "${descriptor.term}"
        val actionSequence = ActionList(name)
        saveToCache(descriptor, actionSequence)

        return if (generateObject(actionSequence, descriptor, generationDepth)) actionSequence
        else UnknownSequence(name, descriptor.type.getKfgType(types), descriptor).also {
            saveToCache(descriptor, it)
        }
    }

    class StackWrapper(val value: GeneratorContext.ExecutionStack<ObjectDescriptor>) {
        override fun hashCode(): Int = 0

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StackWrapper

            if (value.instance eq other.value.instance && value.depth <= other.value.depth) return true
            return false
        }
    }

    fun GeneratorContext.ExecutionStack<ObjectDescriptor>.wrap() = StackWrapper(this)

    open fun checkCtors(
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

    open fun applyMethods(
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

        for (method in klass.accessibleMethods) {
            method.executeAsSetter(current)?.let {
                acceptExecResult(method, it, searchDepth)
            }
            method.executeAsMethod(current)?.let {
                acceptExecResult(method, it, searchDepth)
            }
        }
        return stackList
    }

    fun generateObject(sequence: ActionList, descriptor: ObjectDescriptor, generationDepth: Int): Boolean = with(context) {
        val fallbacks = mutableSetOf<List<CodeAction>>()

        descriptor.concretize(cm)
        descriptor.reduce()

        log.debug("Generating $descriptor")

        val klass = descriptor.klass.kfgClass(types)
        if (klass.orderedCtors.isEmpty()) {
            return false
        }

        val setters = when {
            useSetters -> descriptor.generateSetters(sequence, generationDepth)
            else -> listOf()
        }
        val queue = queueOf(GeneratorContext.ExecutionStack(descriptor, setters, 0))
        val cache = mutableSetOf<StackWrapper>()
        while (queue.isNotEmpty()) {
            val es = queue.poll()
            if (es.wrap() in cache) continue

            if (queue.size > maxQuerySize) {
                break
            }

            cache += es.wrap()
            val (desc, stack, depth) = es
            val current = descriptor.accept(desc)
            current.reduce()
            if (depth >= maxStackSize) continue
            log.debug("Depth $generationDepth, stack depth $depth, query size ${queue.size}")


            if (checkCtors(sequence, klass, current, stack, fallbacks, generationDepth)) {
                return true
            }

            if (depth >= maxStackSize - 1) continue

            for (execStack in applyMethods(sequence, klass, current, stack, depth, generationDepth)) {
                queue += execStack
            }
        }

        return if (fallbacks.isNotEmpty()) {
            sequence.clear()
            sequence += fallbacks.random()
            true
        } else false
    }

    fun generateArgs(args: List<Descriptor>, depth: Int): List<ActionSequence>? = try {
        args.map { fallback.generate(it, depth) }
    } catch (e: SearchLimitExceededException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    fun ObjectDescriptor.checkCtor(klass: Class, method: Method, generationDepth: Int): CodeAction? =
        with(context) {
            val (thisDesc, args) = method.executeAsConstructor(this@checkCtor) ?: return null

            if ((thisDesc as ObjectDescriptor).isFinal(this@checkCtor)) {
                log.debug("Found constructor $method for $this, generating arguments $args")
                when {
                    method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                    else -> {
                        val generatedArgs = generateArgs(args, generationDepth + 1) ?: return null
                        ConstructorCall(method, generatedArgs)
                    }
                }
            } else null
        }

    fun ObjectDescriptor.checkExternalCtor(method: Method, generationDepth: Int): CodeAction? =
        with(context) {
            val (_, args) = method.executeAsExternalConstructor(this@checkExternalCtor) ?: return null

            val generatedArgs = generateArgs(args, generationDepth + 1) ?: return null
            ExternalConstructorCall(method, generatedArgs)
        }

    @Suppress("UNUSED_PARAMETER")
    fun ObjectDescriptor.generateSetters(sequence: ActionList, generationDepth: Int): List<CodeAction> = with(context) {
        val calls = mutableListOf<CodeAction>()
        val kfgKlass = klass.kfgClass(types)
        for ((field, value) in fields.toMap()) {
            val kfgField = kfgKlass.getField(field.first, field.second.getKfgType(types))

            if (visibilityLevel <= kfgField.visibility) {
                log.debug("Directly setting field $field value")
                calls += FieldSetter(kfgField, fallback.generate(value, generationDepth + 1))
                fields.remove(field)
                reduce()

            } else if (kfgField.hasSetter && visibilityLevel <= kfgField.setter.visibility) {
                log.info("Using setter for $field")

                val (result, args) = kfgField.setter.executeAsSetter(this@generateSetters) ?: continue
                val objectDescriptor = result as? ObjectDescriptor
                if (objectDescriptor != null && field notIn objectDescriptor) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to this@generateSetters) }
                    val generatedArgs = generateArgs(args.map { it.deepCopy(remapping()) }, generationDepth + 1)
                        ?: continue
                    calls += MethodCall(kfgField.setter, generatedArgs)
                    accept(result)
                    reduce()
                    log.info("Used setter for field $field, new desc: $this")
                }
            }
        }
        return calls
    }
}