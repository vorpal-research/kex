package org.jetbrains.research.kex.reanimator.callstack

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.reanimator.collector.externalConstructors
import org.jetbrains.research.kex.reanimator.collector.hasSetter
import org.jetbrains.research.kex.reanimator.collector.setter
import org.jetbrains.research.kex.reanimator.descriptor.ArrayDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.ObjectDescriptor
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val maxStackSize by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 5) }
private val maxQuerySize by lazy { kexConfig.getIntValue("apiGeneration", "maxQuerySize", 1000) }

interface Generator {
    val context: GeneratorContext

    fun supports(type: KexType): Boolean
    fun generate(descriptor: Descriptor, generationDepth: Int = 0): CallStack
}

class AnyGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(type: KexType) = true

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()

        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        descriptor.cache(callStack)
        callStack.generateObject(descriptor, generationDepth)
        return callStack
    }

    class StackWrapper(val value: GeneratorContext.ExecutionStack) {
        override fun hashCode(): Int = 0

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StackWrapper

            if (value.instance eq other.value.instance && value.depth <= other.value.depth) return true
            return false
        }
    }

    fun GeneratorContext.ExecutionStack.wrap() = StackWrapper(this)

    private fun CallStack.generateObject(descriptor: ObjectDescriptor, generationDepth: Int) = with(context) {
        val original = descriptor.deepCopy()

        descriptor.concretize(cm)
        descriptor.reduce()

        log.debug("Generating $descriptor")

        val klass = descriptor.klass.kfgClass(types)
        if ((klass.accessibleConstructors + klass.externalConstructors).isEmpty()) {
            this@generateObject += UnknownCall(klass.type, original)
            return
        }

        val setters = descriptor.generateSetters(generationDepth)
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
            if (depth > maxStackSize) continue
            log.debug("Depth $generationDepth, stack depth $depth, query size ${queue.size}")

            val constructors = klass.accessibleConstructors
            val externalConstructors = klass.externalConstructors

            val nonRecursiveConstructors = constructors.filter {
                it.argTypes.all { arg -> !(klass.type.isSupertypeOf(arg) || arg.isSupertypeOf(klass.type)) }
            }
            val nonRecursiveExternalConstructors = externalConstructors.filter {
                it.argTypes.all { arg -> !(klass.type.isSupertypeOf(arg) || arg.isSupertypeOf(klass.type)) }
            }

            val recursiveConstructors = constructors.filter { it !in nonRecursiveConstructors }
            val recursiveExternalConstructors = externalConstructors.filter { it !in nonRecursiveExternalConstructors }

            for (method in nonRecursiveConstructors) {
                val apiCall = current.checkConstructor(klass, method, generationDepth) ?: continue
                this@generateObject.stack += (stack + apiCall).reversed()
                return
            }

            for (method in nonRecursiveExternalConstructors) {
                val apiCall = current.checkExternalConstructor(method, generationDepth) ?: continue
                this@generateObject.stack += (stack + apiCall).reversed()
                return
            }

            for (method in recursiveConstructors) {
                val apiCall = current.checkConstructor(klass, method, generationDepth) ?: continue
                this@generateObject.stack += (stack + apiCall).reversed()
                return
            }

            for (method in recursiveExternalConstructors) {
                val apiCall = current.checkExternalConstructor(method, generationDepth) ?: continue
                this@generateObject.stack += (stack + apiCall).reversed()
                return
            }

            if (depth > maxStackSize - 1) continue

            val acceptExecResult = { method: Method, res: GeneratorContext.ExecutionResult, oldDepth: Int ->
                val (result, args) = res
                if (result != null && result neq current) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to current) }
                    val generatedArgs = args.map { fallback.generate(it.deepCopy(remapping()), generationDepth + 1) }
                    val newStack = stack + MethodCall(method, generatedArgs)
                    val newDesc = result.merge(current)
                    queue += GeneratorContext.ExecutionStack(newDesc, newStack, oldDepth + 1)
                }
            }

            for (method in klass.accessibleMethods) {
                val result = method.executeAsSetter(current) ?: continue
                acceptExecResult(method, result, depth)
            }

            for (method in klass.accessibleMethods) {
                val result = method.executeAsMethod(current) ?: continue
                acceptExecResult(method, result, depth)
            }
        }

        this@generateObject += UnknownCall(klass.type, original)
    }

    private fun ObjectDescriptor.checkConstructor(klass: Class, method: Method, generationDepth: Int): ApiCall? = with(context) {
        val (thisDesc, args) = method.executeAsConstructor(this@checkConstructor) ?: return null

        if (thisDesc.isFinal(this@checkConstructor)) {
            log.debug("Found constructor $method for $this, generating arguments $args")
            when {
                method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                else -> {
                    val generatedArgs = args.map { fallback.generate(it, generationDepth + 1) }
                    ConstructorCall(klass, method, generatedArgs)
                }
            }
        } else null
    }

    private fun ObjectDescriptor.checkExternalConstructor(method: Method, generationDepth: Int): ApiCall? =
            with(context) {
                val (_, args) = method.executeAsExternalConstructor(this@checkExternalConstructor) ?: return null

                val generatedArgs = args.map { fallback.generate(it, generationDepth + 1) }
                ExternalConstructorCall(method, generatedArgs)
            }

    private fun ObjectDescriptor.generateSetters(generationDepth: Int): List<ApiCall> = with(context) {
        val calls = mutableListOf<ApiCall>()
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
                if (result != null && (result[field] == null || result[field] == field.second.defaultDescriptor)) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to this@generateSetters) }
                    val generatedArgs = args.map { fallback.generate(it.deepCopy(remapping()), generationDepth + 1) }
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

class ArrayGenerator(private val fallback: Generator) : Generator {
    override val context get() = fallback.context

    override fun supports(type: KexType) = type is KexArray

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? ArrayDescriptor ?: throw IllegalArgumentException()

        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        descriptor.cache(callStack)

        val elementType = descriptor.elementType.getKfgType(types)
        val lengthCall = PrimaryValue(descriptor.length)
        val array = NewArray(types.getArrayType(elementType), lengthCall)
        callStack += array

        descriptor.elements.forEach { (index, value) ->
            val indexCall = PrimaryValue(index)
            val arrayWrite = ArrayWrite(indexCall, fallback.generate(value, generationDepth + 1))
            callStack += arrayWrite
        }

        callStack
    }
}

class StringGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(type: KexType) = type == KexClass("java/lang/String")

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        descriptor as? ObjectDescriptor ?: throw IllegalArgumentException()
        descriptor.reduce()

        val name = "${descriptor.term}"
        val callStack = CallStack(name)
        descriptor.cache(callStack)

        val stringClass = context.cm["java/lang/String"]

        val valueDescriptor = descriptor["value", KexArray(KexChar())]
        if (valueDescriptor == null) {
            callStack += DefaultConstructorCall(stringClass).wrap(name)
            return callStack
        }
        val value = fallback.generate(valueDescriptor, generationDepth + 1)

        val constructor = stringClass.getMethod("<init>", MethodDesc(arrayOf(types.getArrayType(types.charType)), types.voidType))
        callStack += ConstructorCall(stringClass, constructor, listOf(value)).wrap(name)
        return callStack
    }
}