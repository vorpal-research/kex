package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.type.Type

enum class Visibility {
    PRIVATE,
    PROTECTED,
    PACKAGE,
    PUBLIC;
}

val Node.visibility: Visibility
    get() = when {
        this.isPrivate -> Visibility.PRIVATE
        this.isProtected -> Visibility.PROTECTED
        this.isPublic -> Visibility.PUBLIC
        else -> Visibility.PACKAGE
    }

val Class.isInstantiable: Boolean
    get() = when {
        this.isAbstract -> false
        this.isInterface -> false
        !this.isStatic && this.outerClass != null -> false
        else -> true
    }


fun Descriptor.concrete(cm: ClassManager, map: MutableMap<Descriptor, Descriptor> = mutableMapOf()) = when (this) {
    in map -> map[this]!!
    is ObjectDescriptor -> this.instantiableDescriptor(cm, map)
    else -> this
}

fun ObjectDescriptor.instantiableDescriptor(cm: ClassManager, map: MutableMap<Descriptor, Descriptor> = mutableMapOf()): ObjectDescriptor {
    val concreteClass = when {
        this.klass.isInstantiable -> this.klass
        else -> ConcreteInstanceGenerator[this.klass]
    }
    val result = ObjectDescriptor(klass = concreteClass)
    map[this] = result
    for ((name, desc) in this.fields) {
        result[name] = desc.copy(owner = result, value = desc.value.concrete(cm, map))
    }
    return result
}

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val maxStackSize by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 5) }
private val isInliningEnabled by lazy { kexConfig.getBooleanValue("smt", "ps-inlining", true) }
private val annotationsEnabled by lazy { kexConfig.getBooleanValue("annotations", "enabled", false) }

class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    private val descriptorMap = mutableMapOf<Descriptor, Node>()

    val Method.stateFieldAccesses: Set<Field> get() {
        val methodState = psa.builder(this).methodState ?: return setOf()
        val typeInfoMap = TypeInfoMap(methodState.run {
            val (t, a) = collectArguments(this)
            val map = mutableMapOf<Term, Set<TypeInfo>>()
            val thisType = this@stateFieldAccesses.`class`.kexType
            map += (t ?: term { `this`(thisType) }) to setOf(CastTypeInfo(thisType))
            for ((_, arg) in a) {
                map += arg to setOf(CastTypeInfo(arg.type))
            }
            map
        })

        val transformed = transform(methodState) {
            +AnnotationIncluder(this@stateFieldAccesses, AnnotationManager.defaultLoader)
            +FullDepthInliner(context, typeInfoMap, psa)
        }
        return collectFieldAccesses(context, transformed)
    }

    private fun prepareState(method: Method, ps: PredicateState, ignores: Set<Term> = setOf()) = transform(ps) {
        if (annotationsEnabled) +AnnotationIncluder(method, AnnotationManager.defaultLoader)
        if (isInliningEnabled) +MethodInliner(psa)
        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, context.loader, ignores)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
    }

    private fun prepareQuery(ps: PredicateState) = transform(ps) {
        +NullityInfoAdapter()
        +ArrayBoundsAdapter()
    }

    private class Node(var stack: CallStack) {
        constructor() : this(CallStack())

        operator fun plusAssign(apiCall: ApiCall) {
            this.stack += apiCall
        }

        operator fun plusAssign(callStack: CallStack) {
            this.stack += callStack
        }
    }

    fun generate(descriptor: Descriptor): CallStack {
        if (descriptorMap.containsKey(descriptor)) return descriptorMap.getValue(descriptor).stack

        when (descriptor) {
            is ConstantDescriptor -> return when (descriptor) {
                is ConstantDescriptor.Null -> PrimaryValue(null)
                is ConstantDescriptor.Bool -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Int -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Long -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Float -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Double -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Class -> PrimaryValue(descriptor.value)
            }.wrap()
            is ObjectDescriptor -> {
                descriptorMap[descriptor] = Node(generateObject(descriptor)
                        ?: UnknownCall(descriptor.klass, descriptor).wrap())
            }
            is ArrayDescriptor -> {
                val callStack = Node()
                descriptorMap[descriptor] = callStack

                val elementType = descriptor.type.element
                val array = NewArray(elementType.getKfgType(context.types), PrimaryValue(descriptor.length).wrap()).wrap()
                callStack += array

                descriptor.elements.forEach { (index, value) ->
                    val arrayWrite = ArrayWrite(array, PrimaryValue(index).wrap(), generate(value))
                    callStack += arrayWrite
                }
            }
            is FieldDescriptor -> {
                val callStack = Node()
                val klass = descriptor.klass
                val field = klass.getField(descriptor.name, descriptor.kfgType)
                descriptorMap[descriptor] = callStack

                callStack += when {
                    field.isStatic -> StaticFieldSetter(klass, field, generate(descriptor.value))
                    else -> FieldSetter(klass, generate(descriptor.owner), field, generate(descriptor.value))
                }
            }
        }
        return descriptorMap.getValue(descriptor).stack
    }

    private fun generateObject(descriptor: ObjectDescriptor): CallStack? {
        val reducedDescriptor = descriptor.instantiableDescriptor(context.cm).reduced as ObjectDescriptor
        log.debug("Generating $reducedDescriptor")
        val klass = reducedDescriptor.klass
        val externalConstructors = klass.externalConstructors

        val queue = queueOf(generateSetters(reducedDescriptor))
        while (queue.isNotEmpty()) {
            val (desc, stack) = queue.poll()
            if (stack.stack.size > maxStackSize) continue

            // try to generate constructor call
            for (method in klass.accessibleConstructors.sortedBy { it.argTypes.size }) {
                val (thisDesc, args) = method.executeAsConstructor(desc) ?: continue

                if (thisDesc.isFinal(desc)) {
                    log.debug("Found constructor $method for $descriptor, generating arguments $args")
                    val constructorCall = when {
                        method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                        else -> ConstructorCall(klass, method, args.map { generate(it) })
                    }
                    return (stack + constructorCall).reversed()
                }
            }

            // execute external constructors
            for (method in externalConstructors.sortedBy { it.argTypes.size }) {
                val (_, args) = method.executeAsExternalConstructor(desc) ?: continue

                val constructorCall = ExternalConstructorCall(method, args.map { generate(it) })
                return (stack + constructorCall).reversed()
            }

            // execute available methods
            for (method in klass.accessibleMethods) {
                val (result, args) = method.executeAsSetter(desc) ?: continue
                if (result != null && result != desc) {
                    val newStack = stack + MethodCall(stack, method, args.map { generate(it) })
                    val newDesc = result.merge(desc)
                    queue += newDesc to newStack
                }
            }

            // execute available methods
            for (method in klass.accessibleMethods) {
                val (result, args) = method.executeAsMethod(desc) ?: continue
                if (result != null && result != desc) {
                    val newStack = stack + MethodCall(stack, method, args.map { generate(it) })
                    val newDesc = result.merge(desc)
                    queue += newDesc to newStack
                }
            }
        }
        return null
    }

    private fun generateSetters(descriptor: ObjectDescriptor): Pair<ObjectDescriptor, CallStack> {
        var desc = descriptor
        val targetFields = desc.fields.toList()
        var callStack = CallStack()
        for ((name, fd) in targetFields) {
            val field = desc.klass.getField(name, fd.kfgType)
            if (field.hasSetter && visibilityLevel <= field.setter.visibility) {
                log.info("Using setter for $field")
                val newDesc = ObjectDescriptor(desc.klass)
                newDesc[name] = fd.copy(owner = newDesc)

                val (result, args) = field.setter.executeAsSetter(newDesc) ?: continue
                if (result != null && result != desc) {
                    callStack += MethodCall(callStack, field.setter, args.map { generate(it) })
                    val newFields = desc.fields.filter { it.key != name }.toMutableMap()
                    desc = desc.copy(fieldsInner = newFields)
                    log.info("Used setter for field $field, new desc: $desc")
                }
            }
        }
        return desc to callStack
    }

    private val Class.accessibleConstructors get() = constructors
            .filter { visibilityLevel <= it.visibility }
            .filterNot { it.isSynthetic }

    private val Class.externalConstructors
        get() = cm.concreteClasses
                .flatMap { it.allMethods }
                .filter { it.isStatic && it.returnType.isSubtypeOf(this.type) && it.argTypes.all { arg -> !arg.isSubtypeOf(this.type) } }
                .filterNot { it.isSynthetic }
                .toSet()

    private val Class.accessibleMethods get() = methods
            .filterNot { it.isStatic }
            .filter { visibilityLevel <= it.visibility }
            .filterNot { it.isSynthetic }

    private fun Method.executeAsConstructor(descriptor: ObjectDescriptor): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (isEmpty()) return null
        log.debug("Executing constructor $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = mapper.apply(descriptor.typeInfo + descriptor.preState)
        val state = preState + mapper.apply(methodState ?: return null)

        val preStateFieldTerms = collectFieldTerms(context, preState)
        val preparedState = prepareState(this, state, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.executeAsExternalConstructor(descriptor: ObjectDescriptor): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (isEmpty()) return null
        log.debug("Executing external constructor $this for $descriptor")

        val externalMapper = TermRemapper(
                mapOf(
                        descriptor.term to term { `return`(this@executeAsExternalConstructor) }
                )
        )
        val state = externalMapper.apply(descriptor.typeInfo + (methodState ?: return null))

        val preparedState = prepareState(this, state)
        val preparedQuery = prepareQuery(externalMapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.executeAsSetter(descriptor: ObjectDescriptor): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (isEmpty()) return null
        log.debug("Executing method $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = getSetterPreState(descriptor) ?: return null
        val preStateFieldTerms = collectFieldTerms(context, preState)
        val state = mapper.apply(descriptor.typeInfo + preState + (methodState ?: return null))

        val preparedState = prepareState(this, state, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.executeAsMethod(descriptor: ObjectDescriptor): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (isEmpty()) return null
        log.debug("Executing method $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = getMethodPreState(descriptor) ?: return null
        val preStateFieldTerms = collectFieldTerms(context, preState)
        val state = mapper.apply(descriptor.typeInfo + preState + (methodState ?: return null))

        val preparedState = prepareState(this, state, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.execute(state: PredicateState, query: PredicateState): Pair<ObjectDescriptor?, List<Descriptor>>? {
        val checker = Checker(this, context.loader, psa)
        return when (val result = checker.check(state + query)) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                val (thisDescriptor, argumentDescriptors) =
                        generateInitialDescriptors(this, context, result.model, checker.state)
                (thisDescriptor as? ObjectDescriptor) to argumentDescriptors
            }
            else -> null
        }
    }

    private val Method.methodState get() = psa.builder(this).methodState

    private fun Method.getSetterPreState(descriptor: ObjectDescriptor): PredicateState? {
        val mapper = descriptor.mapper
        val fieldAccessList = this.stateFieldAccesses
        val intersection = descriptor.fields.values.filter {
            fieldAccessList.find { field -> it.name == field.name && it.klass == field.`class` } != null
        }
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for (field in intersection) {
            preStateBuilder.run {
                axiom { field.term.initialize(field.type.defaultDescriptor.term) }
            }
        }
        return mapper.apply(preStateBuilder.apply())
    }

    private fun Method.getMethodPreState(descriptor: ObjectDescriptor): PredicateState? {
        val mapper = descriptor.mapper
        val fieldAccessList = this.stateFieldAccesses
        val intersection = descriptor.fields.values.filter {
            fieldAccessList.find { field -> it.name == field.name && it.klass == field.`class` } != null
        }
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for (field in intersection) {
            preStateBuilder.run {
                axiom { field.term.initialize(generate(field.type)) }
            }
        }
        return mapper.apply(preStateBuilder.apply())
    }

    private val ObjectDescriptor.preState: PredicateState
        get() {
            val preState = StateBuilder()
            for (field in fields.values) {
                preState.run {
                    axiom { field.term.initialize(field.type.defaultDescriptor.term) }
                }
            }

            return preState.apply()
        }

    private val ObjectDescriptor.mapper get() = TermRemapper(mapOf(term to term { `this`(term.type) }))

    private fun ObjectDescriptor?.isFinal(original: ObjectDescriptor) = when {
        this == null -> true
        original.fields.all { this[it.key]?.isDefault ?: return@all true } -> true
        else -> false
    }

    private val Descriptor.reduced: Descriptor
        get() = cachedReduced(mutableMapOf())

    private fun Descriptor.cachedReduced(map: MutableMap<Descriptor, Descriptor>): Descriptor = when (this) {
        in map -> map[this]!!
        is ObjectDescriptor -> {
            val filteredFields = fields.filterNot { (_, field) -> field.isDefault }
            val newObject = ObjectDescriptor(klass)
            map[this] = newObject
            for ((name, field) in filteredFields) {
                newObject[name] = field.copy(owner = newObject, value = field.value.cachedReduced(map))
            }
            newObject
        }
        is ArrayDescriptor -> {
            val filteredElements = this.elements.filterValues { it != this.elementType.defaultDescriptor }
            val newArray = ArrayDescriptor(this.length, this.kfgType, filteredElements.toMutableMap())
            map[this] = newArray
            newArray
        }
        else -> this
    }

    private val FieldDescriptor.isDefault get() = value == type.defaultDescriptor

    private val Type.defaultDescriptor get() = kexType.defaultDescriptor

    private val KexType.defaultDescriptor: Descriptor
        get() = descriptor(context) {
            default(this@defaultDescriptor)
        }
}