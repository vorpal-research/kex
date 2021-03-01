package org.jetbrains.research.kex.reanimator.callstack

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import com.abdullin.kthelper.tryOrNull
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexArray
import org.jetbrains.research.kex.ktype.KexChar
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.collector.externalConstructors
import org.jetbrains.research.kex.reanimator.collector.hasSetter
import org.jetbrains.research.kex.reanimator.collector.setter
import org.jetbrains.research.kex.reanimator.descriptor.*
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.term.FieldTerm
import org.jetbrains.research.kex.state.transformer.TypeInfoMap
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kex.util.loadKClass
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.type.ClassType

private val maxStackSize by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 5) }
private val maxQuerySize by lazy { kexConfig.getIntValue("apiGeneration", "maxQuerySize", 1000) }
private val useRecConstructors by lazy { kexConfig.getBooleanValue("apiGeneration", "use-recursive-constructors", false) }

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

    val List<ApiCall>.isComplete get() = CallStack("", this.toMutableList()).isComplete

    private fun CallStack.generateObject(descriptor: ObjectDescriptor, generationDepth: Int) = with(context) {
        val original = descriptor.deepCopy()
        val fallbacks = mutableSetOf<List<ApiCall>>()

        descriptor.concretize(cm)
        descriptor.reduce()

        log.debug("Generating $descriptor")

        val klass = descriptor.klass.kfgClass(types)
        if (visibilityLevel > klass.visibility) {
            this@generateObject += UnknownCall(klass.type, original)
            return
        }

        if ((klass.accessibleConstructors + klass.externalConstructors).isEmpty()) {
            this@generateObject += UnknownCall(klass.type, original)
            return
        }

        val constructors = klass.accessibleConstructors
        val externalConstructors = klass.externalConstructors

        val nonRecursiveConstructors = constructors.filter {
            it.argTypes.all { arg -> !(klass.type.isSupertypeOf(arg) || arg.isSupertypeOf(klass.type)) }
        }
        val nonRecursiveExternalConstructors = externalConstructors.filter {
            it.argTypes.all { arg -> !(klass.type.isSupertypeOf(arg) || arg.isSupertypeOf(klass.type)) }
        }

        val recursiveConstructors = when {
            useRecConstructors -> constructors.filter { it !in nonRecursiveConstructors }
            else -> listOf()
        }
        val recursiveExternalConstructors = externalConstructors.filter { it !in nonRecursiveExternalConstructors }

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
            current.reduce()
            if (depth > maxStackSize) continue
            log.debug("Depth $generationDepth, stack depth $depth, query size ${queue.size}")

            val checkCtor = fun(ctors: List<Method>, handler: (ctor: Method) -> ApiCall?): Boolean {
                for (method in ctors) {
                    val apiCall = handler(method) ?: continue
                    val result = (stack + apiCall).reversed()
                    if (result.isComplete) {
                        this@generateObject.stack += (stack + apiCall).reversed()
                        return true
                    } else {
                        fallbacks += result
                    }
                }
                return false
            }

            if (checkCtor(nonRecursiveConstructors) { current.checkConstructor(klass, it, generationDepth) }) {
                return
            }

            if (checkCtor(nonRecursiveExternalConstructors) { current.checkExternalConstructor(it, generationDepth) }) {
                return
            }

            if (checkCtor(recursiveConstructors) { current.checkConstructor(klass, it, generationDepth) }) {
                return
            }

            if (checkCtor(recursiveExternalConstructors) { current.checkExternalConstructor(it, generationDepth) }) {
                return
            }

            if (depth > maxStackSize - 1) continue

            val acceptExecResult = { method: Method, res: GeneratorContext.ExecutionResult, oldDepth: Int ->
                val (result, args) = res
                if (result != null && result neq current) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to current) }
                    val generatedArgs = generateArgs(args.map { it.deepCopy(remapping()) }, generationDepth + 1)
                    if (generatedArgs != null) {
                        val newStack = stack + MethodCall(method, generatedArgs)
                        val newDesc = result.merge(current)
                        queue += GeneratorContext.ExecutionStack(newDesc, newStack, oldDepth + 1)
                    }
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

        if (fallbacks.isNotEmpty()) {
            this@generateObject.stack += fallbacks.random()
        } else {
            this@generateObject += UnknownCall(klass.type, original)
        }
    }

    private fun generateArgs(args: List<Descriptor>, depth: Int): List<CallStack>? = tryOrNull {
        args.map { fallback.generate(it, depth) }
    }

    private fun ObjectDescriptor.checkConstructor(klass: Class, method: Method, generationDepth: Int): ApiCall? = with(context) {
        val (thisDesc, args) = method.executeAsConstructor(this@checkConstructor) ?: return null

        if (thisDesc.isFinal(this@checkConstructor)) {
            log.debug("Found constructor $method for $this, generating arguments $args")
            when {
                method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                else -> {
                    val generatedArgs = generateArgs(args, generationDepth + 1) ?: return null
                    ConstructorCall(klass, method, generatedArgs)
                }
            }
        } else null
    }

    private fun ObjectDescriptor.checkExternalConstructor(method: Method, generationDepth: Int): ApiCall? =
            with(context) {
                val (_, args) = method.executeAsExternalConstructor(this@checkExternalConstructor) ?: return null

                val generatedArgs = generateArgs(args, generationDepth + 1) ?: return null
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

    override fun supports(type: KexType) = type.toString() == "java/lang/String"

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

class EnumGenerator(private val fallback: Generator) : Generator {
    companion object {
        private val enumConstants = mutableMapOf<KexType, Map<FieldTerm, Descriptor>>()

        private fun getEnumConstants(ctx: GeneratorContext, enumType: KexType): Map<FieldTerm, Descriptor> = enumConstants.getOrPut(enumType) {
            computeEnumConstants(ctx, enumType)
        }

        private fun computeEnumConstants(ctx: GeneratorContext, enumType: KexType): Map<FieldTerm, Descriptor> = with(ctx) {
            val kfgType = enumType.getKfgType(context.types) as ClassType
            val staticInit = kfgType.`class`.getMethod("<clinit>", "()V")

            val state = staticInit.methodState ?: return mapOf()
            val preparedState = state.prepare(staticInit, TypeInfoMap())
            val queryBuilder = StateBuilder()
            with(queryBuilder) {
                val enumArray = KexArray(descriptor.type)
                val valuesField = term { `class`(descriptor.type).field(enumArray, "\$VALUES") }
                val generatedTerm = term { generate(enumArray) }
                state { generatedTerm equality valuesField.load() }
                require { generatedTerm inequality null }
            }
            val preparedQuery = prepareQuery(queryBuilder.apply())

            val checker = Checker(staticInit, context.loader, psa)
            val params = when (val result = checker.check(preparedState + preparedQuery)) {
                is Result.SatResult -> {
                    log.debug("Model: ${result.model}")
                    generateFinalDescriptors(staticInit, context, result.model, checker.state)
                }
                else -> null
            } ?: return mapOf()

            return params.staticFields
                    .mapNotNull { if (it.value is StaticFieldDescriptor) (it.key to it.value as StaticFieldDescriptor) else null }
                    .map { it.first to it.second.value }
                    .toMap()
        }
    }

    override val context: GeneratorContext
        get() = fallback.context

    override fun supports(type: KexType): Boolean {
        val kfgType = type.getKfgType(context.types)
        return when (kfgType) {
            is ClassType -> kfgType.`class`.isEnum
            else -> false
        }
    }

    private fun Descriptor.matches(other: Descriptor, visited: MutableMap<Pair<Descriptor, Descriptor>, Boolean>): Boolean = when {
        this.javaClass != other.javaClass -> {
            visited[this to other] = false
            false
        }
        this is ConstantDescriptor.Null -> other is ConstantDescriptor.Null
        this is ConstantDescriptor.Bool -> this.value == (other as? ConstantDescriptor.Bool)?.value
        this is ConstantDescriptor.Byte -> this.value == (other as? ConstantDescriptor.Byte)?.value
        this is ConstantDescriptor.Char -> this.value == (other as? ConstantDescriptor.Char)?.value
        this is ConstantDescriptor.Short -> this.value == (other as? ConstantDescriptor.Short)?.value
        this is ConstantDescriptor.Int -> this.value == (other as? ConstantDescriptor.Int)?.value
        this is ConstantDescriptor.Long -> this.value == (other as? ConstantDescriptor.Long)?.value
        this is ConstantDescriptor.Float -> this.value == (other as? ConstantDescriptor.Float)?.value
        this is ConstantDescriptor.Double -> this.value == (other as? ConstantDescriptor.Double)?.value
        this is ObjectDescriptor -> {
            other as ObjectDescriptor
            visited[this to other] = false
            val res = other.fields.all { (field, value) ->
                val thisField = this[field] ?: return@all false
                value.matches(thisField, visited)
            }
            visited[this to other] = res
            res
        }
        this is ArrayDescriptor -> {
            other as ArrayDescriptor
            visited[this to other] = false
            if (this.length == other.length) {
                val res = other.elements.all { (index, value) ->
                    val thisField = this[index] ?: return@all false
                    value.matches(thisField, visited)
                }
                visited[this to other] = res
                res
            } else {
                false
            }
        }
        else -> false
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        val name = descriptor.term.toString()
        val cs = CallStack(name)
        descriptor.cache(cs)

        val kfgType = descriptor.type.getKfgType(context.types) as ClassType
        val enumConstants = getEnumConstants(this, descriptor.type).toList()

        val result = enumConstants.firstOrNull { it.second.matches(descriptor, mutableMapOf()) }
                ?: enumConstants.randomOrNull()
                ?: return cs.also { it += UnknownCall(kfgType, descriptor).wrap(name) }
        cs += EnumValueCreation(cm[result.first.klass], result.first.fieldNameString)
        return cs
    }
}

class KtObjectGenerator(private val fallback: Generator) : Generator {
    override val context: GeneratorContext
        get() = fallback.context

    val types get() = context.types
    val loader get() = context.loader

    override fun supports(type: KexType): Boolean {
        val kClass = tryOrNull { loader.loadKClass(types, type) } ?: return false
        return kClass.isCompanion || kClass.objectInstance != null
    }

    override fun generate(descriptor: Descriptor, generationDepth: Int): CallStack = with(context) {
        val name = descriptor.term.toString()
        val cs = CallStack(name)
        descriptor.cache(cs)

        val kfgType = descriptor.type.getKfgType(types) as? ClassType
                ?: return cs.also { it += UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name) }
        val kClass = tryOrNull { loader.loadKClass(kfgType) }
                ?: return cs.also { it += UnknownCall(kfgType, descriptor).wrap(name) }

        if (kClass.isCompanion) {
            val (parentClass, companionName) = kfgType.`class`.fullname.split("\$")
            val kfgParent = cm[parentClass]
            cs += StaticFieldGetter(kfgParent, companionName)
        } else if (kClass.objectInstance != null) {
            cs += StaticFieldGetter(kfgType.`class`, "INSTANCE")
        }
        return cs
    }
}
