package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import java.util.*

enum class Visibility {
    PRIVATE,
    PROTECTED,
    PACKAGE,
    PUBLIC;
}

val Method.visibility: Visibility
    get() = when {
        this.isPrivate -> Visibility.PRIVATE
        this.isProtected -> Visibility.PROTECTED
        this.isPublic -> Visibility.PUBLIC
        else -> Visibility.PACKAGE
    }

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }
private val maxStackSize: Int by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 10) }
private val isInliningEnabled by lazy { kexConfig.getBooleanValue("smt", "ps-inlining", true) }
private val annotationsEnabled by lazy { kexConfig.getBooleanValue("annotations", "enabled", false) }

// todo: generation of abstract classes and interfaces
// todo: think about generating list of calls instead of call stack tree
// todo: complex relations between descriptors (not just equals to constant, but also equals to each other)
class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    private val descriptorMap = mutableMapOf<Descriptor, Node>()

    private fun prepareState(method: Method, ps: PredicateState, ignores: Set<Term> = setOf()) = transform(ps) {
        if (annotationsEnabled) {
            +AnnotationIncluder(AnnotationManager.defaultLoader)
        }

        if (isInliningEnabled) {
            +MethodInliner(method, psa)
        }

        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, context.loader, ignores)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
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
                descriptorMap[descriptor] = Node(generateObject(descriptor) ?: UnknownCall(descriptor).wrap())
            }
            is ArrayDescriptor -> {
                val callStack = Node()
                descriptorMap[descriptor] = callStack

                val elementType = (descriptor.type as ArrayType).component
                val array = NewArray(elementType, PrimaryValue(descriptor.length).wrap()).wrap()
                callStack += array

                descriptor.elements.forEach { (index, value) ->
                    val arrayWrite = ArrayWrite(array, PrimaryValue(index).wrap(), generate(value))
                    callStack += arrayWrite
                }
            }
            is FieldDescriptor -> {
                val callStack = Node()
                val klass = descriptor.klass
                val field = klass.getField(descriptor.name, descriptor.type)
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
        val klass = descriptor.klass

        val queue = ArrayDeque<Pair<ObjectDescriptor, CallStack>>()
        queue += descriptor.reduce() to CallStack()
        while (queue.isNotEmpty()) {
            val (desc, stack) = queue.pollFirst()

            if (stack.stack.size > maxStackSize) continue

            generateConstructorCall(desc)?.run {
                return stack + this
            }

            for (method in klass.methods.filter { visibilityLevel <= it.visibility }) {
                val (newDesc, args) = method.executeAsMethod(desc) ?: continue
                if (newDesc != null && newDesc != desc) {
                    queue += newDesc.merge(desc) to (stack + MethodCall(stack, method, args.map { generate(it) }))
                }
            }
        }
        return null
    }

    private fun generateConstructorCall(descriptor: ObjectDescriptor): ApiCall? {
        val klass = descriptor.klass
        val preState = StateBuilder()
        for (field in descriptor.fields.values) {
            preState.run {
                val tempTerm = TermGenerator.nextTerm(field.type.kexType)
                state { tempTerm equality field.term.load() }
                assume { tempTerm equality field.type.kexType.defaultDescriptor.term }
            }
        }

        for (method in klass.constructors.filter { visibilityLevel <= it.visibility }) {
            val (thisDesc, args) = method.executeAsConstructor(descriptor, preState.apply()) ?: continue

            if (thisDesc.isFinal(descriptor)) {
                return when {
                    method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                    else -> ConstructorCall(klass, method, args.map { generate(it) })
                }
            }
        }
        return null
    }

    private fun Method.executeAsConstructor(descriptor: ObjectDescriptor, preState: PredicateState):
            Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (isEmpty()) return descriptor to listOf()
        log.debug("Executing constructor $this for $descriptor")
        val builder = psa.builder(this)
        val mapper = TermRemapper(mapOf(descriptor.term to term { `this`(descriptor.term.type) }))
        val state = mapper.apply(
                preState + (builder.methodState ?: return null)
        )

        val checker = Checker(this, context.loader, psa)
        val preStateFieldTerms = collectFieldTerms(context, mapper.apply(preState))
        val preparedState = prepareState(this, state, preStateFieldTerms)
        val preparedQuery = mapper.apply(descriptor.toState())

        return when (val result = checker.check(preparedState + preparedQuery)) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                val (thisDescriptor, argumentDescriptors) =
                        generateInitialDescriptors(this, context, result.model, checker.state)
                (thisDescriptor as? ObjectDescriptor)?.reduce() to argumentDescriptors
            }
            else -> {
                log.warn("Could not use $this for generating $descriptor")
                null
            }
        }
    }

    private fun Method.executeAsMethod(descriptor: ObjectDescriptor): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (isEmpty()) return descriptor to listOf()
        log.debug("Executing method $this for $descriptor")
        val builder = psa.builder(this)
        val mapper = TermRemapper(mapOf(descriptor.term to term { `this`(descriptor.term.type) }))
        val state = mapper.apply(builder.methodState ?: return null)
        val query = mapper.apply(descriptor.toState())

        val checker = Checker(this, context.loader, psa)
        val preparedState = prepareState(this, state)

        val fieldAccessList = collectFieldAccesses(context, preparedState)
        val intersection = descriptor.fields.values.filter {
            fieldAccessList.find { field -> it.name == field.name && it.klass == field.`class` } != null
        }
        if (intersection.isEmpty()) return null

        val preStateBuilder = StateBuilder()
        for (field in intersection) {
            preStateBuilder.run {
                val tempTerm = TermGenerator.nextTerm(field.type.kexType)
                state { tempTerm equality field.term.load() }
                assume { tempTerm equality field.type.defaultDescriptor.term }
            }
        }
        val preState = mapper.apply(preStateBuilder.apply())
        val preStateFieldTerms = collectFieldTerms(context, preState)

        return when (val result = checker.check(prepareState(this, preState + state, preStateFieldTerms) + query)) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                val (thisDescriptor, argumentDescriptors) =
                        generateInitialDescriptors(this, context, result.model, checker.state)
                (thisDescriptor as? ObjectDescriptor)?.reduce() to argumentDescriptors
            }
            else -> {
                log.warn("Could not use $this for generating $descriptor")
                null
            }
        }
    }

    private fun ObjectDescriptor?.isFinal(original: ObjectDescriptor) = when {
        this == null -> true
        original.fields.all {
            (this[it.key]?.value ?: return@all true) == it.value.type.defaultDescriptor
        } -> true
        else -> false
    }

    private fun ObjectDescriptor.reduce(): ObjectDescriptor {
        val filteredFields = fields.filterNot { it.value.value == it.value.type.defaultDescriptor }
        val newObject = ObjectDescriptor(klass)
        for ((name, field) in filteredFields) {
            newObject[name] = field.copy(owner = newObject)
        }
        return newObject
    }

    private val Type.defaultDescriptor get() = kexType.defaultDescriptor

    private val KexType.defaultDescriptor: Descriptor
        get() = descriptor(context) {
            default(this@defaultDescriptor)
        }
}