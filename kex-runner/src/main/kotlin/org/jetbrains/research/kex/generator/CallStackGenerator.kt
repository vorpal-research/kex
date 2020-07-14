package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.generator.descriptor.*
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.axiom
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.with
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import java.util.*

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val maxStackSize by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 5) }
private val maxGenerationDepth by lazy { kexConfig.getIntValue("apiGeneration", "maxGenerationDepth", 100) }
private val isInliningEnabled by lazy { kexConfig.getBooleanValue("smt", "ps-inlining", true) }
private val annotationsEnabled by lazy { kexConfig.getBooleanValue("annotations", "enabled", false) }

private typealias ExecResult = Pair<ObjectDescriptor?, List<Descriptor>>
private typealias ExecStack = Triple<ObjectDescriptor, List<ApiCall>, Int>

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

// todo: setters for static fields
class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    val cm get() = context.cm
    val types get() = context.types

    private val fieldSetterCache = mutableMapOf<Field, Set<Method>>()
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

    fun generate(descriptor: Descriptor, depth: Int = 0): CallStack {
        val name = "${descriptor.term}"

        if (descriptor in descriptorMap) return descriptorMap.getValue(descriptor)
        if (depth > maxGenerationDepth) return UnknownCall(descriptor.type.getKfgType(types), descriptor).wrap(name)

        when (descriptor) {
            is ConstantDescriptor -> return when (descriptor) {
                is ConstantDescriptor.Null -> PrimaryValue(null)
                is ConstantDescriptor.Bool -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Int -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Long -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Float -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Double -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Class -> PrimaryValue(descriptor.value)
            }.wrap(name)
            is ObjectDescriptor -> {
                val callStack = CallStack(name)
                descriptorMap[descriptor] = callStack
                callStack.generateObject(descriptor, depth)
            }
            is ArrayDescriptor -> {
                val callStack = CallStack(name)
                descriptorMap[descriptor] = callStack

                val elementType = descriptor.elementType
                val array = NewArray(elementType.getKfgType(context.types), PrimaryValue(descriptor.length).wrap("${name}Length"))
                callStack += array

                descriptor.elements.forEach { (index, value) ->
                    val arrayWrite = ArrayWrite(PrimaryValue(index).wrap("${name}Index"), generate(value, depth + 1))
                    callStack += arrayWrite
                }
            }
            is StaticFieldDescriptor -> {
                val callStack = CallStack(name)
                descriptorMap[descriptor] = callStack
                val kfgClass = descriptor.klass.kfgClass(types)
                val kfgField = kfgClass.getField(descriptor.field, descriptor.type.getKfgType(types))
                callStack += StaticFieldSetter(kfgClass, kfgField, generate(descriptor.value, depth + 1))
            }
        }
        return descriptorMap.getValue(descriptor)
    }

    private fun CallStack.generateObject(descriptor: ObjectDescriptor, generationDepth: Int) {
        val original = descriptor.deepCopy()

        descriptor.concretize(cm)
        descriptor.reduce()

        log.debug("Generating $descriptor")

        val setters = descriptor.generateSetters(generationDepth)
        val klass = descriptor.klass.kfgClass(types)
        val queue = queueOf(descriptor to setters with 0)
        while (queue.isNotEmpty()) {
            val (desc, stack, depth) = queue.poll()
            val current = descriptor.accept(desc)
            if (depth > maxStackSize) continue

            for (method in klass.accessibleConstructors) {
                val (thisDesc, args) = method.executeAsConstructor(current) ?: continue

                if (thisDesc.isFinal(current)) {
                    log.debug("Found constructor $method for $descriptor, generating arguments $args")
                    val constructorCall = when {
                        method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                        else -> ConstructorCall(klass, method, args.map { generate(it, generationDepth + 1) })
                    }
                    this.stack += (stack + constructorCall).reversed()
                    return
                }
            }

            for (method in klass.externalConstructors) {
                val (_, args) = method.executeAsExternalConstructor(current) ?: continue

                val constructorCall = ExternalConstructorCall(method, args.map { generate(it, generationDepth + 1) })
                this.stack += (stack + constructorCall).reversed()
                return
            }

            for (method in klass.accessibleMethods) {
                val result = method.executeAsSetter(current) ?: continue
                acceptExecutionResult(result, current, depth, generationDepth, stack, method, queue)
            }

            for (method in klass.accessibleMethods) {
                val result = method.executeAsMethod(current) ?: continue
                acceptExecutionResult(result, current, depth, generationDepth, stack, method, queue)
            }
        }

        this += UnknownCall(klass.type, original)
    }

    private fun acceptExecutionResult(res: ExecResult, current: ObjectDescriptor, oldDepth: Int, generationDepth: Int,
                                      stack: List<ApiCall>, method: Method, queue: Queue<ExecStack>) {
        val (result, args) = res
        if (result != null) {
            val remapping = { mutableMapOf<Descriptor, Descriptor>(result to current) }
            val newStack = stack + MethodCall(method, args.map { generate(it.deepCopy(remapping()), generationDepth + 1) })
            val newDesc = result.merge(current)
            queue += newDesc to newStack with (oldDepth + 1)
        }
    }

    private fun ObjectDescriptor.generateSetters(generationDepth: Int): List<ApiCall> {
        val calls = mutableListOf<ApiCall>()
        val kfgKlass = this.klass.kfgClass(types)
        for ((field, value) in this.fields.toMap()) {
            val kfgField = kfgKlass.getField(field.first, field.second.getKfgType(types))

            if (visibilityLevel <= kfgField.visibility) {
                log.debug("Directly setting field $field value")
                calls += FieldSetter(kfgField, generate(value, generationDepth + 1))
                this.fields.remove(field)
                this.reduce()

            } else if (kfgField.hasSetter && visibilityLevel <= kfgField.setter.visibility) {
                log.info("Using setter for $field")

                val (result, args) = kfgField.setter.executeAsSetter(this) ?: continue
                if (result != null && (result[field] == null || result[field] == field.second.defaultDescriptor)) {
                    val remapping = { mutableMapOf<Descriptor, Descriptor>(result to this) }
                    calls += MethodCall(kfgField.setter, args.map { generate(it.deepCopy(remapping()), generationDepth + 1) })
                    this.accept(result)
                    this.reduce()
                    log.info("Used setter for field $field, new desc: $this")
                }
            }
        }
        return calls
    }

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

    private fun Method.executeAsConstructor(descriptor: ObjectDescriptor): ExecResult? {
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

    private fun Method.executeAsExternalConstructor(descriptor: ObjectDescriptor): ExecResult? {
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

    private fun Method.execute(descriptor: ObjectDescriptor, preStateGetter: (Method, ObjectDescriptor) -> PredicateState?): ExecResult? {
        if (isEmpty()) return null
        log.debug("Executing method $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = preStateGetter(this, descriptor) ?: return null
        val preStateFieldTerms = collectFieldTerms(context, preState)
        val state = mapper.apply(descriptor.typeInfo + preState + (methodState ?: return null))

        val preparedState = prepareState(this, state, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.executeAsSetter(descriptor: ObjectDescriptor) =
            this.execute(descriptor) { method, objectDescriptor -> method.getSetterPreState(objectDescriptor) }

    private fun Method.executeAsMethod(descriptor: ObjectDescriptor) =
            this.execute(descriptor) { method, objectDescriptor -> method.getMethodPreState(objectDescriptor) }

    private fun Method.execute(state: PredicateState, query: PredicateState): ExecResult? {
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
        val intersection = descriptor.fields.filter { (key, _) ->
            fieldAccessList.find { field -> key.first == field.name } != null
        }.toMap()
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for ((field, _) in intersection) {
            val fieldTerm = term { descriptor.term.field(field.second, field.first) }
            preStateBuilder += axiom { fieldTerm.initialize(initializer(field.second)) }
        }
        return mapper.apply(preStateBuilder.apply())
    }

    private val ObjectDescriptor.preState: PredicateState
        get() {
            val preState = StateBuilder()
            for ((field, _) in fields) {
                val fieldTerm = term { term.field(field.second, field.first) }
                preState += axiom { fieldTerm.initialize(field.second.defaultValue) }
            }

            return preState.apply()
        }

    private val ObjectDescriptor.mapper get() = TermRemapper(mapOf(term to term { `this`(term.type) }))

    private fun ObjectDescriptor?.isFinal(original: ObjectDescriptor) = when {
        this == null -> true
        original.fields.all { this[it.key] == null || this[it.key] == descriptor { default(it.key.second) } } -> true
        else -> false
    }

    private val KexType.defaultDescriptor: Descriptor
        get() = descriptor { default(this@defaultDescriptor) }

    private val KexType.defaultValue: Term get() = defaultDescriptor.term
}