package org.jetbrains.research.kex.reanimator.callstack.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.reanimator.Parameters
import org.jetbrains.research.kex.reanimator.callstack.ApiCall
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.collector.externalCtors
import org.jetbrains.research.kex.reanimator.descriptor.*
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
import org.jetbrains.research.kthelper.logging.log

private val useRecCtors by lazy {
    kexConfig.getBooleanValue(
        "apiGeneration",
        "use-recursive-constructors",
        false
    )
}

class GeneratorContext(
    val context: ExecutionContext,
    val psa: PredicateStateAnalysis,
    val visibilityLevel: Visibility
) {
    val cm get() = context.cm
    val types get() = context.types
    val loader get() = context.loader

    private val descriptorCache = mutableMapOf<Descriptor, CallStack>()

    data class ExecutionStack<T : FieldContainingDescriptor<T>>(
        val instance: T,
        val calls: List<ApiCall>,
        val depth: Int
    )

    fun saveToCache(descriptor: Descriptor, stack: CallStack) {
        descriptorCache[descriptor] = stack
    }

    fun getFromCache(descriptor: Descriptor) = descriptorCache[descriptor]

    fun PredicateState.prepare(method: Method, typeInfoMap: TypeInfoMap, ignores: Set<Term> = setOf()) =
        prepareState(method, this, typeInfoMap, ignores)

    fun prepareState(method: Method, ps: PredicateState, typeInfoMap: TypeInfoMap, ignores: Set<Term> = setOf()) =
        transform(ps) {
            val staticTypeInfo = collectStaticTypeInfo(types, ps, typeInfoMap)
            +AnnotationAdapter(method, AnnotationManager.defaultLoader)
            +ConcreteImplInliner(types, staticTypeInfo, psa)
            +StaticFieldInliner(cm, psa)
            +RecursiveInliner(psa) { MethodInliner(psa, inlineIndex = it) }
            +IntrinsicAdapter
            +ReflectionInfoAdapter(method, context.loader, ignores)
            +Optimizer()
            +ConstantPropagator
            +BoolTypeAdapter(types)
            +ConstStringAdapter()
            +ArrayBoundsAdapter()
            +NullityInfoAdapter()
            +FieldNormalizer(context.cm, "state.normalized")
        }

    fun prepareQuery(ps: PredicateState) = transform(ps) {
        +NullityInfoAdapter()
        +ArrayBoundsAdapter()
        +FieldNormalizer(context.cm, "query.normalized")
    }

    val Class.allCtors get() = accessibleCtors + externalCtors

    private val klass2Constructors = mutableMapOf<Class, List<Method>>()

    val Class.nonRecursiveCtors get() = accessibleCtors.filterNot { it.isRecursive }

    val Class.orderedCtors
        get() = klass2Constructors.getOrPut(this) {
            val nonRecursiveCtors = nonRecursiveCtors
            val nonRecursiveExtCtors = externalCtors.filter {
                it.argTypes.all { arg -> !(type.isSupertypeOf(arg) || arg.isSupertypeOf(type)) }
            }

            val recursiveCtors = when {
                useRecCtors -> accessibleCtors.filter { it !in nonRecursiveCtors }
                else -> listOf()
            }
            val recursiveExtCtors = externalCtors.filter { it !in nonRecursiveExtCtors }
            nonRecursiveCtors + nonRecursiveExtCtors + recursiveCtors + recursiveExtCtors
        }

    val Class.staticMethods
        get() = methods
            .filter { it.isStatic }
            .filter { visibilityLevel <= it.visibility }

    val Class.accessibleCtors
        get() = constructors
            .filter { visibilityLevel <= it.visibility }
            .sortedBy { it.argTypes.size }

    val Class.accessibleMethods
        get() = methods
            .filterNot { it.isStatic }
            .filter { visibilityLevel <= it.visibility }

    val Method.isRecursive get() = argTypes.any { arg -> `class`.type.isSupertypeOf(arg) || arg.isSupertypeOf(`class`.type) }

    private val Method.argTypeInfo
        get() = this.parameters.map {
            val type = it.type.kexType
            term { arg(type, it.index) } to type.concretize(cm)
        }.toMap()

    fun Method.executeAsConstructor(descriptor: ObjectDescriptor): Parameters<Descriptor>? {
        if (isEmpty()) return null
        log.debug("Executing constructor $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = mapper.apply(descriptor.preState)
        val typeInfoState = mapper.apply(descriptor.typeInfo)
        val typeInfos = collectPlainTypeInfos(types, typeInfoState) + this.argTypeInfo
        val state = preState + mapper.apply(methodState ?: return null) + typeInfoState

        val preStateFieldTerms = collectFieldTerms(context, preState)
        val preparedState = state.prepare(this, typeInfos, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    fun Method.executeAsExternalConstructor(descriptor: ObjectDescriptor): Parameters<Descriptor>? {
        if (isEmpty()) return null
        log.debug("Executing external constructor $this for $descriptor")

        val externalMapper = TermRemapper(
            mapOf(
                descriptor.term to term { `return`(this@executeAsExternalConstructor) }
            )
        )
        val typeInfoState = externalMapper.apply(descriptor.typeInfo)
        val typeInfos = collectPlainTypeInfos(types, typeInfoState) + this.argTypeInfo
        val state = externalMapper.apply(methodState ?: return null) + typeInfoState

        val preparedState = state.prepare(this, typeInfos)
        val preparedQuery = prepareQuery(externalMapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.execute(
        descriptor: ObjectDescriptor,
        preStateGetter: (Method, ObjectDescriptor) -> PredicateState?
    ): Parameters<Descriptor>? {
        if (isEmpty()) return null
        log.debug("Executing method $this for $descriptor")

        val mapper = descriptor.mapper
        val preState = preStateGetter(this, descriptor) ?: return null
        val preStateFieldTerms = collectFieldTerms(context, preState)
        val typeInfoState = mapper.apply(descriptor.typeInfo)
        val typeInfos = collectPlainTypeInfos(types, typeInfoState) + this.argTypeInfo
        val state = mapper.apply(preState + (methodState ?: return null)) + typeInfoState

        val preparedState = state.prepare(this, typeInfos, preStateFieldTerms)
        val preparedQuery = prepareQuery(mapper.apply(descriptor.query))
        return execute(preparedState, preparedQuery)
    }

    private fun Method.getStaticPreState(descriptor: ClassDescriptor, initializer: (KexType) -> Term): PredicateState? {
        val fieldAccessList = this.collectFieldAccesses(context, psa)
        val intersection = descriptor.fields.filter { (key, _) ->
            fieldAccessList.find { field -> key.first == field.name } != null
        }.toMap()
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for ((field, _) in intersection) {
            val fieldTerm = term { descriptor.term.field(field.second, field.first) }
            preStateBuilder += axiom { fieldTerm.initialize(initializer(field.second)) }
            preStateBuilder += axiom { fieldTerm.load() equality initializer(field.second) }
        }
        return preStateBuilder.apply()
    }

    private fun Method.executeAsStatic(
        descriptor: ClassDescriptor,
        preStateGetter: (Method, ClassDescriptor) -> PredicateState?
    ): Parameters<Descriptor>? {
        if (isEmpty()) return null
        log.debug("Executing method $this for $descriptor")

        val preState = preStateGetter(this, descriptor) ?: return null
        val preStateFieldTerms = collectFieldTerms(context, preState)
        val typeInfoState = descriptor.typeInfo
        val typeInfos = collectPlainTypeInfos(types, typeInfoState) + this.argTypeInfo
        val state = (preState + (methodState ?: return null)) + typeInfoState

        val preparedState = state.prepare(this, typeInfos, preStateFieldTerms)
        val preparedQuery = prepareQuery(descriptor.query)
        return execute(preparedState, preparedQuery)
    }

    fun Method.executeAsSetter(descriptor: ObjectDescriptor) =
        this.execute(descriptor) { method, objectDescriptor -> method.getSetterPreState(objectDescriptor) }

    fun Method.executeAsMethod(descriptor: ObjectDescriptor) =
        this.execute(descriptor) { method, objectDescriptor -> method.getMethodPreState(objectDescriptor) }

    fun Method.executeAsStaticSetter(descriptor: ClassDescriptor) =
        this.executeAsStatic(descriptor) { method, classDescriptor -> method.getStaticSetterPreState(classDescriptor) }

    fun Method.executeAsStaticMethod(descriptor: ClassDescriptor) =
        this.executeAsStatic(descriptor) { method, classDescriptor -> method.getStaticMethodPreState(classDescriptor) }

    private fun Method.execute(state: PredicateState, query: PredicateState): Parameters<Descriptor>? {
        val checker = Checker(this, context.loader, psa)
        val checkedState = state + query
        return when (val result = checker.check(checkedState)) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                generateInitialDescriptors(this, context, result.model, checker.state)
            }
            else -> null
        }
    }

    val Method.methodState get() = psa.builder(this).methodState

    private fun Method.getSetterPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { it.defaultValue }

    private fun Method.getMethodPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { term { generate(it) } }

    private fun Method.getStaticSetterPreState(descriptor: ClassDescriptor) = getStaticPreState(descriptor) { it.defaultValue }

    private fun Method.getStaticMethodPreState(descriptor: ClassDescriptor) = getStaticPreState(descriptor) { term { generate(it) } }

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
            +RecursiveInliner(psa) { ConcreteImplInliner(types, typeInfoMap, psa, inlineIndex = it) }
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
            preStateBuilder += axiom { fieldTerm.load() equality initializer(field.second) }
        }
//        for ((field, value) in descriptor.fields) {
//            if (value is ArrayDescriptor) {
//                val fieldTerm = term { descriptor.term.field(field.second, field.first) }
//                val genLoad = term { generate(field.second) }
//                preStateBuilder += state { genLoad equality fieldTerm.load() }
//                if (value.elements.size > 1) {
//                    for ((index1, elem1) in value.elements) {
//                        for ((index2, elem2) in value.elements) {
//                            if (index1 != index2 && elem1 != elem2) {
//                                val elemIndex1 = term { genLoad[index1] }
//                                val elemIndex2 = term { genLoad[index2] }
//                                val load1 = term { elemIndex1.load() }
//                                val load2 = term { elemIndex2.load() }
//                                val genLoad1 = term { generate(load1.type) }
//                                val genLoad2 = term { generate(load2.type) }
//                                preStateBuilder += state { genLoad1 equality load1 }
//                                preStateBuilder += state { genLoad2 equality load2 }
//                                preStateBuilder += axiom { genLoad1 inequality genLoad2 }
//                            }
//                        }
//                    }
//                } else if (value.elements.size == 1) {
//                    for ((index1, elem1) in value.elements) {
//                        val elemIndex1 = term { genLoad[index1] }
//                        val load1 = term { elemIndex1.load() }
//                        val genLoad1 = term { generate(load1.type) }
//                        preStateBuilder += state { genLoad1 equality null }
//                    }
//                }
//            }
//        }
        return mapper.apply(preStateBuilder.apply())
    }

    val List<ApiCall>.isComplete get() = CallStack("", this.toMutableList()).isComplete

    private val ObjectDescriptor.preState: PredicateState
        get() {
            val preState = StateBuilder()
            for ((field, _) in fields) {
                val fieldTerm = term { term.field(field.second, field.first) }
                preState += axiom { fieldTerm.initialize(field.second.defaultValue) }
                preState += axiom { fieldTerm.load() equality field.second.defaultValue }
            }

            return preState.apply()
        }

    private val ObjectDescriptor.mapper get() = TermRemapper(mapOf(term to term { `this`(term.type) }))

    infix fun <T : FieldContainingDescriptor<T>> Pair<String, KexType>.notIn(descriptor: T) =
        this !in descriptor.fields || descriptor[this] == second.defaultDescriptor

    fun <T : FieldContainingDescriptor<T>> T?.isFinal(original: T) =
        when {
            this == null -> true
            original.fields.all { it.key notIn this } -> true
            else -> false
        }

    val KexType.defaultDescriptor: Descriptor
        get() = descriptor { default(this@defaultDescriptor) }

    val KexType.defaultValue: Term get() = defaultDescriptor.term
}