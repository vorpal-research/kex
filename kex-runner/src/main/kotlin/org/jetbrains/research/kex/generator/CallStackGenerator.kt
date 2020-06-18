package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.axiom
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method

val Class.isInstantiable: Boolean
    get() = when {
        this.isAbstract -> false
        this.isInterface -> false
        !this.isStatic && this.outerClass != null -> false
        else -> true
    }

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val maxStackSize by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 5) }
private val isInliningEnabled by lazy { kexConfig.getBooleanValue("smt", "ps-inlining", true) }
private val annotationsEnabled by lazy { kexConfig.getBooleanValue("annotations", "enabled", false) }

private fun Method.collectFieldAccesses(context: ExecutionContext, psa: PredicateStateAnalysis): Set<Field> {
    val methodState = psa.builder(this).methodState ?: return setOf()
    val typeInfoMap = TypeInfoMap(methodState.run {
        val (t, a) = collectArguments(this)
        val map = mutableMapOf<Term, Set<TypeInfo>>()
        val thisType = `class`.kexType
        map += (t ?: term { `this`(thisType) }) to setOf(CastTypeInfo(thisType))
        for ((_, arg) in a) {
            map += arg to setOf(CastTypeInfo(arg.type))
        }
        map
    })

    val transformed = transform(methodState) {
        +AnnotationIncluder(this@collectFieldAccesses, AnnotationManager.defaultLoader)
        +DepthInliner(context, typeInfoMap, psa)
    }
    return collectFieldAccesses(context, transformed)
}

// todo: rework call stack generation for recursive data structures
class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    val cm get() = context.cm
    val types get() = context.types

    private val descriptorMap = mutableMapOf<Descriptor, CallStack>()

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

    fun generate(descriptor: Descriptor): CallStack {
        if (descriptor in descriptorMap) return descriptorMap.getValue(descriptor)

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
                val callStack = CallStack()
                descriptorMap[descriptor] = callStack
                callStack.generateObject(descriptor)
            }
            is ArrayDescriptor -> {
                val callStack = CallStack()
                descriptorMap[descriptor] = callStack

                val elementType = descriptor.elementType
                val array = NewArray(elementType.getKfgType(context.types), PrimaryValue(descriptor.length).wrap()).wrap()
                callStack += array

                descriptor.elements.forEach { (index, value) ->
                    val arrayWrite = ArrayWrite(array, PrimaryValue(index).wrap(), generate(value))
                    callStack += arrayWrite
                }
            }
            // TODO: static fields
//            is FieldDescriptor -> {
//                val callStack = Node()
//                val klass = descriptor.klass
//                val field = klass.getField(descriptor.name, descriptor.kfgType)
//                descriptorMap[descriptor] = callStack
//
//                callStack += when {
//                    field.isStatic -> StaticFieldSetter(klass, field, generate(descriptor.value))
//                    else -> FieldSetter(klass, generate(descriptor.owner), field, generate(descriptor.value))
//                }
//            }
        }
        return descriptorMap.getValue(descriptor)
    }

    private fun CallStack.generateObject(descriptor: ObjectDescriptor) {
        val original = descriptor.deepCopy()

        descriptor.concretize(cm)
        descriptor.reduce()

        log.debug("Generating $descriptor")

        val klass = descriptor.klass.kfgClass(types)

//        val setters = descriptor.generateSetters()
        val queue = queueOf(descriptor to listOf<ApiCall>())
        while (queue.isNotEmpty()) {
            val (desc, stack) = queue.poll()
            val current = descriptor.accept(desc)
            if (stack.size > maxStackSize) continue

            for (method in klass.accessibleConstructors) {
                val (thisDesc, args) = method.executeAsConstructor(current) ?: continue

                if (thisDesc.isFinal(current)) {
                    log.debug("Found constructor $method for $descriptor, generating arguments $args")
                    val constructorCall = when {
                        method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                        else -> ConstructorCall(klass, method, args.map { generate(it) })
                    }
                    this.stack += (stack + constructorCall).reversed()
                    return
                }
            }

            for (method in klass.externalConstructors) {
                val (_, args) = method.executeAsExternalConstructor(current) ?: continue

                val constructorCall = ExternalConstructorCall(method, args.map { generate(it) })
                this.stack += (stack + constructorCall).reversed()
                return
            }

            for (method in klass.accessibleMethods) {
                val (result, args) = method.executeAsSetter(current) ?: continue
                if (result != null) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to current) }
                    val newStack = stack + MethodCall(method, args.map { generate(it.deepCopy(remapping())) })
                    val newDesc = result.merge(current)
                    queue += newDesc to newStack
                }
            }

            for (method in klass.accessibleMethods) {
                val (result, args) = method.executeAsMethod(current) ?: continue
                if (result != null) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to current) }
                    val newStack = stack + MethodCall(method, args.map { generate(it.deepCopy(remapping())) })
                    val newDesc = result.merge(current)
                    queue += newDesc to newStack
                }
            }
        }

        this += UnknownCall(klass, original)
    }

//    private fun ObjectDescriptor.generateSetters(): List<ApiCall> {
//        val callStack = mutableListOf<ApiCall>()
//        val kfgKlass = klass.kfgClass(types)
//
//        for ((name, value) in fields.toMap()) {
//            val field = kfgKlass.getField(name, value.type.getKfgType(types))
//            if (!field.hasSetter || visibilityLevel > field.setter.visibility) continue
//
//            log.info("Using setter for $field")
////            val newDesc = this.copyWithField(name)
//
//            val (result, args) = field.setter.executeAsSetter(this) ?: continue
//            if (result != null && (result[name] == null || result[name] == value.type.defaultDescriptor)) {
//                callStack += MethodCall(field.setter, args.map { generate(it) })
//                this.remove(name)
//                log.info("Used setter for field $field, new desc: $this")
//            }
//        }
//        return callStack
//    }

    private val Class.accessibleConstructors
        get() = constructors
                .filter { visibilityLevel <= it.visibility }
                .filterNot { it.isSynthetic }
                .sortedBy { it.argTypes.size }

    private val Class.accessibleMethods
        get() = methods
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

    private fun Method.getSetterPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { it.defaultValue }

    private fun Method.getMethodPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { term { generate(it) } }

    private fun Method.getPreState(descriptor: ObjectDescriptor, initializer: (KexType) -> Term): PredicateState? {
        val mapper = descriptor.mapper
        val fieldAccessList = this.collectFieldAccesses(context, psa)
        val intersection = descriptor.fields.filter {
            fieldAccessList.find { field -> it.key == field.name } != null
        }.toMap()
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for ((field, value) in intersection) {
            val fieldTerm = term { descriptor.term.field(value.type, field) }
            preStateBuilder += axiom { fieldTerm.initialize(initializer(value.type)) }
        }
        return mapper.apply(preStateBuilder.apply())
    }

    private val ObjectDescriptor.preState: PredicateState
        get() {
            val preState = StateBuilder()
            for ((field, value) in fields) {
                val fieldTerm = term { term.field(value.type, field) }
                preState += axiom { fieldTerm.initialize(value.type.defaultValue) }
            }

            return preState.apply()
        }

    private val ObjectDescriptor.mapper get() = TermRemapper(mapOf(term to term { `this`(term.type) }))

    private fun ObjectDescriptor?.isFinal(original: ObjectDescriptor) = when {
        this == null -> true
        original.fields.all { this[it.key]?.isDefault ?: return@all true } -> true
        else -> false
    }

    private val KexType.defaultDescriptor: Descriptor
        get() = descriptor { default(this@defaultDescriptor) }

    private val KexType.defaultValue: Term get() = defaultDescriptor.term
}