package org.jetbrains.research.kex.reanimator.callstack

import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.ObjectDescriptor
import org.jetbrains.research.kex.reanimator.descriptor.descriptor
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

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val recursiveInlining by lazy { kexConfig.getBooleanValue("apiGeneration", "recursiveInlining", false) }

class GeneratorContext(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    val cm get() = context.cm
    val types get() = context.types
    val loader get() = context.loader

    val descriptorCache = mutableMapOf<Descriptor, CallStack>()

    data class ExecutionResult(val instance: ObjectDescriptor?, val arguments: List<Descriptor>)
    data class ExecutionStack(val instance: ObjectDescriptor, val calls: List<ApiCall>, val depth: Int)

    fun Descriptor.cache(stack: CallStack) {
        descriptorCache[this] = stack
    }

    fun Descriptor.cached() = descriptorCache[this]

    fun PredicateState.prepare(method: Method, typeInfoMap: TypeInfoMap, ignores: Set<Term> = setOf()) = when {
        recursiveInlining -> prepareState(method, this, typeInfoMap, ignores)
        else -> prepareState(method, this, ignores)
    }

    fun prepareState(method: Method, ps: PredicateState, typeInfoMap: TypeInfoMap, ignores: Set<Term> = setOf()) = transform(ps) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        val aa = StensgaardAA().also { it.apply(ps) }
        +RecursiveInliner(psa) { AliasingConcreteImplInliner(types, typeInfoMap, aa, psa, it) }
        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, context.loader, ignores)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(types)
        +ConstStringAdapter()
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
    }

    fun prepareState(method: Method, ps: PredicateState, ignores: Set<Term> = setOf()) = transform(ps) {
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +MethodInliner(psa)
        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, context.loader, ignores)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(types)
        +ConstStringAdapter()
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
    }

    fun prepareQuery(ps: PredicateState) = transform(ps) {
        +NullityInfoAdapter()
        +ArrayBoundsAdapter()
    }


    val Class.accessibleConstructors
        get() = constructors
                .filter { visibilityLevel <= it.visibility }
//                .filterNot { it.isSynthetic }
                .sortedBy { it.argTypes.size }

    val Class.accessibleMethods
        get() = methods
                .filterNot { it.isStatic }
                .filter { visibilityLevel <= it.visibility }
//                .filterNot { it.isSynthetic }

    fun Method.executeAsConstructor(descriptor: ObjectDescriptor): ExecutionResult? {
        if (isEmpty()) return null
        log.debug("Executing constructor $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = mapper.apply(descriptor.newTypeInfo + descriptor.preState)
        val typeInfos = collectPlainTypeInfos(types, preState)
        val state = preState + mapper.apply(methodState ?: return null)

        val preStateFieldTerms = collectFieldTerms(context, preState)
        val preparedState = state.prepare(this, typeInfos, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    fun Method.executeAsExternalConstructor(descriptor: ObjectDescriptor): ExecutionResult? {
        if (isEmpty()) return null
        log.debug("Executing external constructor $this for $descriptor")

        val externalMapper = TermRemapper(
                mapOf(
                        descriptor.term to term { `return`(this@executeAsExternalConstructor) }
                )
        )
        val preState = externalMapper.apply(descriptor.newTypeInfo)
        val typeInfos = collectPlainTypeInfos(types, preState)
        val state = preState + externalMapper.apply(methodState ?: return null)

        val preparedState = state.prepare(this, typeInfos)
        val preparedQuery = prepareQuery(externalMapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    fun Method.execute(descriptor: ObjectDescriptor, preStateGetter: (Method, ObjectDescriptor) -> PredicateState?): ExecutionResult? {
        if (isEmpty()) return null
        log.debug("Executing method $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = preStateGetter(this, descriptor) ?: return null
        val preStateFieldTerms = collectFieldTerms(context, preState)
        val typeInfoState = mapper.apply(descriptor.newTypeInfo)
        val typeInfos = collectPlainTypeInfos(types, typeInfoState)
        val state = typeInfoState + mapper.apply(preState + (methodState ?: return null))

        val preparedState = state.prepare(this, typeInfos, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    fun Method.executeAsSetter(descriptor: ObjectDescriptor) =
            this.execute(descriptor) { method, objectDescriptor -> method.getSetterPreState(objectDescriptor) }

    fun Method.executeAsMethod(descriptor: ObjectDescriptor) =
            this.execute(descriptor) { method, objectDescriptor -> method.getMethodPreState(objectDescriptor) }

    fun Method.execute(state: PredicateState, query: PredicateState): ExecutionResult? {
        val checker = Checker(this, context.loader, psa)
        val checkedState = state + query
        return when (val result = checker.check(checkedState)) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                val (thisDescriptor, argumentDescriptors) =
                        generateInitialDescriptors(this, context, result.model, checker.state)
                ExecutionResult(thisDescriptor as? ObjectDescriptor, argumentDescriptors)
            }
            else -> null
        }
    }

    val Method.methodState get() = psa.builder(this).methodState

    fun Method.getSetterPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { it.defaultValue }

    fun Method.getMethodPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { term { generate(it) } }

    fun Method.collectFieldAccesses(context: ExecutionContext, psa: PredicateStateAnalysis): Set<Field> {
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
            +AnnotationAdapter(this@collectFieldAccesses, AnnotationManager.defaultLoader)
            +RecursiveInliner(psa) { ConcreteImplInliner(types, typeInfoMap, psa, it) }
        }
        return collectFieldAccesses(context, transformed)
    }

    fun Method.getPreState(descriptor: ObjectDescriptor, initializer: (KexType) -> Term): PredicateState? {
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

    val ObjectDescriptor.preState: PredicateState
        get() {
            val preState = StateBuilder()
            for ((field, _) in fields) {
                val fieldTerm = term { term.field(field.second, field.first) }
                preState += axiom { fieldTerm.initialize(field.second.defaultValue) }
            }

            return preState.apply()
        }

    val ObjectDescriptor.mapper get() = TermRemapper(mapOf(term to term { `this`(term.type) }))

    fun ObjectDescriptor?.isFinal(original: ObjectDescriptor) = when {
        this == null -> true
        original.fields.all { this[it.key] == null || this[it.key] == descriptor { default(it.key.second) } } -> true
        else -> false
    }

    val KexType.defaultDescriptor: Descriptor
        get() = descriptor { default(this@defaultDescriptor) }

    val KexType.defaultValue: Term get() = defaultDescriptor.term
}