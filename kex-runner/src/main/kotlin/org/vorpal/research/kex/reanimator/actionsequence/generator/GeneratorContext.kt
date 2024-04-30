package org.vorpal.research.kex.reanimator.actionsequence.generator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.annotations.AnnotationManager
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asm.util.accessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ClassDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.FieldContainingDescriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexArray
import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexByte
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexDouble
import org.vorpal.research.kex.ktype.KexFloat
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexLong
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexShort
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.CodeAction
import org.vorpal.research.kex.smt.Checker
import org.vorpal.research.kex.smt.Result
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.predicate.axiom
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.AnnotationAdapter
import org.vorpal.research.kex.state.transformer.ArrayBoundsAdapter
import org.vorpal.research.kex.state.transformer.BasicInvariantsTransformer
import org.vorpal.research.kex.state.transformer.BoolTypeAdapter
import org.vorpal.research.kex.state.transformer.CastTypeInfo
import org.vorpal.research.kex.state.transformer.ClassAdapter
import org.vorpal.research.kex.state.transformer.ClassMethodAdapter
import org.vorpal.research.kex.state.transformer.ConcreteImplInliner
import org.vorpal.research.kex.state.transformer.ConstEnumAdapter
import org.vorpal.research.kex.state.transformer.ConstStringAdapter
import org.vorpal.research.kex.state.transformer.ConstantPropagator
import org.vorpal.research.kex.state.transformer.EqualsTransformer
import org.vorpal.research.kex.state.transformer.FieldNormalizer
import org.vorpal.research.kex.state.transformer.IntrinsicAdapter
import org.vorpal.research.kex.state.transformer.KexIntrinsicsAdapter
import org.vorpal.research.kex.state.transformer.KexRtAdapter
import org.vorpal.research.kex.state.transformer.NullityInfoAdapter
import org.vorpal.research.kex.state.transformer.Optimizer
import org.vorpal.research.kex.state.transformer.RecursiveInliner
import org.vorpal.research.kex.state.transformer.ReflectionInfoAdapter
import org.vorpal.research.kex.state.transformer.StaticFieldInliner
import org.vorpal.research.kex.state.transformer.StringMethodAdapter
import org.vorpal.research.kex.state.transformer.TermRemapper
import org.vorpal.research.kex.state.transformer.TypeInfo
import org.vorpal.research.kex.state.transformer.TypeInfoMap
import org.vorpal.research.kex.state.transformer.TypeNameAdapter
import org.vorpal.research.kex.state.transformer.collectArguments
import org.vorpal.research.kex.state.transformer.collectFieldAccesses
import org.vorpal.research.kex.state.transformer.collectFieldTerms
import org.vorpal.research.kex.state.transformer.collectPlainTypeInfos
import org.vorpal.research.kex.state.transformer.collectStaticTypeInfo
import org.vorpal.research.kex.state.transformer.generateInitialDescriptors
import org.vorpal.research.kex.state.transformer.transform
import org.vorpal.research.kex.util.StringInfoContext
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull

@Suppress("MemberVisibilityCanBePrivate", "unused")
class GeneratorContext(
    val context: ExecutionContext,
    val psa: PredicateStateAnalysis
) : StringInfoContext() {
    private val useRecCtors by lazy {
        kexConfig.getBooleanValue(
            "reanimator",
            "useRecursiveConstructors",
            false
        )
    }

    val cm get() = context.cm
    val types get() = context.types
    val loader get() = context.loader
    val accessLevel get() = context.accessLevel

    private val descriptorCache = mutableMapOf<Descriptor, ActionSequence>()
    private val klass2Constructors = mutableMapOf<Class, List<Method>>()

    data class ExecutionStack<T : FieldContainingDescriptor<T>>(
        val instance: T,
        val calls: List<CodeAction>,
        val depth: Int
    )

    fun saveToCache(descriptor: Descriptor, stack: ActionSequence) {
        descriptorCache[descriptor] = stack
    }

    fun getFromCache(descriptor: Descriptor) = descriptorCache[descriptor]

    fun PredicateState.prepare(method: Method, typeInfoMap: TypeInfoMap, ignores: Set<Term> = setOf()) =
        prepareState(method, this, typeInfoMap, ignores)

    private fun prepareState(
        method: Method,
        ps: PredicateState,
        typeInfoMap: TypeInfoMap,
        ignores: Set<Term> = setOf()
    ) = transform(ps) {
        val staticTypeInfo = collectStaticTypeInfo(types, ps, typeInfoMap)
        +KexRtAdapter(cm)
        +StringMethodAdapter(context.cm)
        +AnnotationAdapter(method, AnnotationManager.defaultLoader)
        +RecursiveInliner(psa) { index, psa ->
            ConcreteImplInliner(types, typeInfoMap + staticTypeInfo, psa, inlineIndex = index)
        }
        +ClassAdapter(cm)
        +StaticFieldInliner(context, psa)
        +IntrinsicAdapter
        +KexIntrinsicsAdapter()
        +EqualsTransformer()
        +BasicInvariantsTransformer(method)
        +ReflectionInfoAdapter(method, context.loader, ignores)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(types)
        +ClassMethodAdapter(context.cm)
        +ConstEnumAdapter(context)
        +ConstStringAdapter(types)
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
        +FieldNormalizer(context.cm, ".state.normalized")
        +TypeNameAdapter(context)
    }

    fun prepareQuery(ps: PredicateState) = transform(ps) {
        +NullityInfoAdapter()
        +ArrayBoundsAdapter()
        +FieldNormalizer(context.cm, ".query.normalized")
    }

    val Method.isOverride get() = klass.allAncestors.any { tryOrNull { it.getMethod(name, desc) } != null }

    val Class.isVisible get() = this@GeneratorContext.accessLevel.canAccess(this.accessModifier)

    val Class.externalCtors get() = instantiationManager.getExternalCtors(this)

    val Class.allCtors get() = accessibleCtors + externalCtors

    val Class.nonRecursiveCtors get() = accessibleCtors.filterNot { it.isRecursive }

    val Class.orderedCtors
        get() = klass2Constructors.getOrPut(this) {
            val nonRecursiveCtors = nonRecursiveCtors
            val nonRecursiveExtCtors = externalCtors.filter {
                it.argTypes.all { arg -> !(asType.isSupertypeOf(arg) || arg.isSupertypeOf(asType)) }
            }

            val recursiveCtors = when {
                useRecCtors -> accessibleCtors.filter { it !in nonRecursiveCtors }
                else -> listOf()
            }
            val recursiveExtCtors = externalCtors.filter { it !in nonRecursiveExtCtors }
            nonRecursiveCtors + nonRecursiveExtCtors + recursiveCtors + recursiveExtCtors
        }

    val Class.staticMethods: Set<Method>
        get() = when {
            this.isVisible -> methods
                .filter { it.isStatic }
                .filterTo(mutableSetOf()) { this@GeneratorContext.accessLevel.canAccess(it.accessModifier) }

            else -> setOf()
        }

    val Class.accessibleCtors: Set<Method>
        get() = when {
            instantiationManager.isDirectlyInstantiable(this, this@GeneratorContext.accessLevel) -> constructors
                .filter { this@GeneratorContext.accessLevel.canAccess(it.accessModifier) }
                .sortedBy { it.argTypes.size }
                .toSet()

            else -> setOf()
        }

    val Class.accessibleMethods: Set<Method>
        get() {
            val visibleMethods = methods
                .filterNot { it.isStatic }
                .filterTo(mutableSetOf()) { this@GeneratorContext.accessLevel.canAccess(it.accessModifier) }
            return when {
                isVisible -> visibleMethods
                else -> visibleMethods.filterTo(mutableSetOf()) { it.isOverride }
            }
        }

    val Method.isRecursive
        get() = argTypes.any { arg ->
            klass.asType.isSupertypeOf(arg) || arg.isSupertypeOf(klass.asType)
        }

    private val Method.argTypeInfo
        get() = this.argTypes.withIndex().associate {
            val type = it.value.kexType
            term { arg(type, it.index) } to instantiationManager.getConcreteType(
                type,
                cm,
                context.accessLevel,
                context.random
            )
        }

    fun Method.executeAsConstructor(descriptor: ObjectDescriptor): Parameters<Descriptor>? {
        if (body.isEmpty()) return null
        log.debug("Executing constructor {} for {}", this, descriptor)

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
        if (body.isEmpty()) return null
        log.debug("Executing external constructor {} for {}", this, descriptor)

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
        if (body.isEmpty()) return null
        log.debug("Executing method {} for {}", this, descriptor)

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

    private fun <T : FieldContainingDescriptor<T>> Method.getPreStateCommon(
        descriptor: T,
        initializer: (KexType) -> Term
    ): PredicateState? {
        val fieldAccessList = this.collectFieldAccesses(context, psa)
        val intersection = descriptor.fields.filter { (key, _) ->
            fieldAccessList.find { field -> key.first == field.name } != null
        }.toMap()
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for ((field, _) in intersection) {
            val fieldTerm = term { descriptor.term.field(field.second, field.first) }
            val initializerTerm = initializer(field.second)
            preStateBuilder += axiom { fieldTerm.initialize(initializerTerm) }
            preStateBuilder += axiom { initializerTerm equality fieldTerm.load() }
        }
        return preStateBuilder.apply()
    }

    private fun Method.getStaticPreState(descriptor: ClassDescriptor, initializer: (KexType) -> Term): PredicateState? =
        this.getPreStateCommon(descriptor, initializer)

    private fun Method.executeAsStatic(
        descriptor: ClassDescriptor,
        preStateGetter: (Method, ClassDescriptor) -> PredicateState?
    ): Parameters<Descriptor>? {
        if (body.isEmpty()) return null
        log.debug("Executing method {} for {}", this, descriptor)

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
        val checker = Checker(this, context, psa)
        val checkedState = state + query
        return when (val result = checker.check(checkedState)) {
            is Result.SatResult -> {
                log.debug("Model: {}", result.model)
                generateInitialDescriptors(this, context, result.model, checker.state).parameters
            }

            else -> null
        }
    }

    val Method.methodState get() = psa.builder(this).methodState

    private fun Method.getSetterPreState(descriptor: ObjectDescriptor) = getPreState(descriptor) { it.defaultValue }

    private fun Method.getMethodPreState(descriptor: ObjectDescriptor) =
        getMethodPreState(descriptor) { term { generate(it) } }

    private fun Method.getStaticSetterPreState(descriptor: ClassDescriptor) =
        getStaticPreState(descriptor) { it.defaultValue }

    private fun Method.getStaticMethodPreState(descriptor: ClassDescriptor) =
        getStaticPreState(descriptor) { term { generate(it) } }

    fun Method.collectFieldAccesses(context: ExecutionContext, psa: PredicateStateAnalysis): Set<Field> {
        val methodState = psa.builder(this).methodState ?: return setOf()
        val typeInfoMap = TypeInfoMap(methodState.run {
            val (t, a) = collectArguments(this)
            val map = mutableMapOf<Term, Set<TypeInfo>>()
            val thisType = klass.kexType
            map += (t ?: term { `this`(thisType) }) to setOf(CastTypeInfo(thisType))
            for ((_, arg) in a) {
                map += arg to setOf(CastTypeInfo(arg.type))
            }
            map
        })

        val transformed = transform(methodState) {
            +KexRtAdapter(cm)
            +AnnotationAdapter(this@collectFieldAccesses, AnnotationManager.defaultLoader)
            +RecursiveInliner(psa) { index, psa ->
                ConcreteImplInliner(types, typeInfoMap, psa, inlineIndex = index)
            }
        }
        return collectFieldAccesses(context, transformed)
    }

    @Suppress("DuplicatedCode")
    fun Method.getMethodPreState(descriptor: ObjectDescriptor, initializer: (KexType) -> Term): PredicateState? {
        val mapper = descriptor.mapper

        val preStateBuilder = this.getPreStateCommon(descriptor, initializer)?.builder() ?: return null

        // experimental check
        val subObjects = linkedSetOf(descriptor.term to descriptor)
        for ((term, desc) in subObjects) {
            for ((field, value) in desc.fields) {
                when (value) {
                    is ObjectDescriptor -> {
                        val fieldTerm = term { term.field(field.second, field.first) }
                        val genLoad = term { generate(field.second) }
                        preStateBuilder += state { genLoad equality fieldTerm.load() }
                        subObjects += genLoad to value
                    }

                    is ArrayDescriptor -> {
                        val fieldTerm = term { term.field(field.second, field.first) }
                        val genLoad = term { generate(field.second) }
                        preStateBuilder += state { genLoad equality fieldTerm.load() }
                        if (value.elements.size > 1) {
                            val term2Element = mutableMapOf<Int, Term>()
                            for ((index, elem) in value.elements) {
                                val elemIndex = term { genLoad[index] }
                                val load = term { elemIndex.load() }
                                val elemLoad = term { generate(load.type) }
                                preStateBuilder += state { elemLoad equality load }
                                term2Element[index] = elemLoad
                                if (elem is ObjectDescriptor)
                                    subObjects += elemLoad to elem
                            }

                            val allPairs =
                                value.elements.keys.flatMap { i1 -> value.elements.keys.map { i2 -> i1 to i2 } }
                                    .filter { it.first != it.second }
                            allPairs.fold(mutableSetOf<Pair<Int, Int>>()) { acc, pair ->
                                if (pair.second to pair.first !in acc) acc += pair
                                acc
                            }.forEach { (index1, index2) ->
                                val elem1 = term2Element[index1]!!
                                val elem2 = term2Element[index2]!!
                                if (value.elements[index1] != value.elements[index2])
                                    preStateBuilder += axiom { elem1 inequality elem2 }
                            }

                        } else if (value.elements.size == 1) {

                            for ((index, elem) in value.elements) {
                                val elemIndex = term { genLoad[index] }
                                val load1 = term { elemIndex.load() }
                                val genLoad1 = term { generate(load1.type) }
                                preStateBuilder += state { genLoad1 equality null }
                                if (elem is ObjectDescriptor)
                                    subObjects += genLoad1 to elem
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
        return mapper.apply(preStateBuilder.apply())
    }

    fun Method.getPreState(descriptor: ObjectDescriptor, initializer: (KexType) -> Term): PredicateState? =
        this.getPreStateCommon(descriptor, initializer)?.also {
            descriptor.mapper.apply(it)
        }

    val List<CodeAction>.isComplete get() = ActionList("", this.toMutableList()).isComplete

    private val ObjectDescriptor.preState: PredicateState
        get() = basic {
            for ((field, _) in fields) {
                val fieldTerm = term { term.field(field.second, field.first) }
                axiom { fieldTerm.initialize(field.second.defaultValue) }
                axiom { field.second.defaultValue equality fieldTerm.load() }
            }
        }

    private val ObjectDescriptor.mapper get() = TermRemapper(mapOf(term to term { `this`(term.type) }))

    infix fun <T : FieldContainingDescriptor<T>> Pair<String, KexType>.notIn(descriptor: T) =
        this !in descriptor.fields || (descriptor[this]!! eq second.defaultDescriptor)

    fun <T : FieldContainingDescriptor<T>> T?.isFinal(original: T) =
        when {
            this == null -> true
            original.fields.all { it.key notIn this } -> true
            else -> false
        }

    val KexType.defaultDescriptor: Descriptor
        get() = descriptor { default(this@defaultDescriptor) }

    val KexType.defaultValue: Term
        get() = term {
            when (this@defaultValue) {
                is KexBool -> const(false)
                is KexByte -> const(0.toByte())
                is KexChar -> const(0.toChar())
                is KexShort -> const(0.toShort())
                is KexInt -> const(0)
                is KexLong -> const(0L)
                is KexFloat -> const(0.0F)
                is KexDouble -> const(0.0)
                is KexClass -> const(null)
                is KexArray -> const(null)
                is KexReference -> reference.defaultValue
                else -> unreachable { log.error("Could not generate default descriptor value for unknown type $this") }
            }
        }

    val Descriptor.asStringValue: String?
        get() = (this as? ObjectDescriptor)?.let { obj ->
            val valueDescriptor = obj[valueArrayName, valueArrayType] as? ArrayDescriptor
            valueDescriptor?.let { array ->
                (0 until array.length).map {
                    when (val value = array.elements.getOrDefault(it, descriptor { const(' ') })) {
                        is ConstantDescriptor.Char -> value.value
                        is ConstantDescriptor.Byte -> value.value.toInt().toChar()
                        else -> unreachable { log.error("Unexpected element type in string: $value") }
                    }
                }.joinToString("")
            }
        }
}
