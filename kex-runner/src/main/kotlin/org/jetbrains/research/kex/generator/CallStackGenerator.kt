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
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import java.util.*

private val maxStackSize: Int by lazy { kexConfig.getIntValue("apiGeneration", "maxStackSize", 10) }
private val isInliningEnabled  by lazy { kexConfig.getBooleanValue("smt", "ps-inlining", true) }
private val annotationsEnabled  by lazy { kexConfig.getBooleanValue("annotations", "enabled", false) }

// todo: think about generating list of calls instead of call stack tree
// todo: complex relations between descriptors (not just equals to constant, but also equals to each other)
// todo: deeper object generation (when fields of an object are also objects)
class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    private val descriptorMap = mutableMapOf<Descriptor, Node>()

    private fun prepareState(method: Method, ps: PredicateState) = transform(ps) {
        if (annotationsEnabled) {
            +AnnotationIncluder(AnnotationManager.defaultLoader)
        }

        if (isInliningEnabled) {
            +MethodInliner(method, psa)
        }

        +IntrinsicAdapter
        if (!method.isConstructor) +ReflectionInfoAdapter(method, context.loader)
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

    // todo: accessibility check
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
                val elementType = (descriptor.type as ArrayType).component
                val array = NewArray(elementType, PrimaryValue(descriptor.length).wrap()).wrap()
                callStack += array
                descriptorMap[descriptor] = Node(array)

                descriptor.elements.forEach { (index, value) ->
                    callStack += ArrayWrite(array, PrimaryValue(index).wrap(), generate(value))
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
        queue += reduce(descriptor) to CallStack()
        while (queue.isNotEmpty()) {
            val (desc, stack) = queue.pollFirst()

            if (stack.stack.size > maxStackSize) continue

            generateConstructorCall(desc)?.run {
                return stack + this
            }

            for (method in klass.methods) {
                val (newDesc, args) = executeMethod(desc, method) ?: continue
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
                assume { tempTerm equality defaultValueForType(field.type.kexType).term }
            }
        }

        for (method in klass.constructors) {
            val (thisDesc, args) = executeConstructor(descriptor, method, preState.apply()) ?: continue
            // todo: proper check for successful generation
            if (thisDesc.isFinal(descriptor)) {
                return when {
                    method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                    else -> ConstructorCall(klass, method, args.map { generate(it) })
                }
            }
        }
        return null
    }

    private fun executeConstructor(descriptor: ObjectDescriptor, method: Method, preState: PredicateState): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (method.isEmpty()) return descriptor to listOf()
        log.debug("Executing constructor $method for $descriptor")
        val builder = psa.builder(method)
        val termMap = mapOf(descriptor.term to term { `this`(descriptor.term.type) })
        val state = TermRemapper(termMap).apply(
                preState + (builder.methodState ?: unreachable { log.error("Can't build state for $method") })
        )

        val checker = Checker(method, context.loader, psa)
        return when (val result = checker.check(prepareState(method, state), TermRemapper(termMap).apply(descriptor.toState()))) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                val (thisDescriptor, argumentDescriptors) = generateInitialDescriptors(method, context, result.model, checker.state)
                (thisDescriptor as? ObjectDescriptor)?.let { reduce(it) } to argumentDescriptors
            }
            else -> {
                log.warn("Could not use $method for generating $descriptor")
                null
            }
        }
    }

    private fun executeMethod(descriptor: ObjectDescriptor, method: Method): Pair<ObjectDescriptor?, List<Descriptor>>? {
        if (method.isEmpty()) return descriptor to listOf()
        log.debug("Executing method $method for $descriptor")
        val builder = psa.builder(method)
        val mapper = TermRemapper(mapOf(descriptor.term to term { `this`(descriptor.term.type) }))
        val state = mapper.apply(builder.methodState ?: unreachable { log.error("Can't build state for $method") })
        val query = mapper.apply(descriptor.toState())

        val checker = Checker(method, context.loader, psa)
        val preparedState = checker.prepareState(state)

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
                assume { tempTerm equality defaultValueForType(field.type.kexType).term }
            }
        }
        val preState = mapper.apply(preStateBuilder.apply())

        return when (val result = checker.check(prepareState(method, preState + state), query)) {
            is Result.SatResult -> {
                log.debug("Model: ${result.model}")
                val (thisDescriptor, argumentDescriptors) = generateInitialDescriptors(method, context, result.model, checker.state)
                (thisDescriptor as? ObjectDescriptor) to argumentDescriptors
            }
            else -> {
                log.warn("Could not use $method for generating $descriptor")
                null
            }
        }
    }

    private fun ObjectDescriptor?.isFinal(original: ObjectDescriptor) = when {
        this == null -> true
        original.fields.all {
            (this[it.key]?.value ?: return@all true) == defaultValueForType(it.value.type.kexType)
        } -> true
        else -> false
    }

    private fun reduce(descriptor: ObjectDescriptor): ObjectDescriptor {
        val filteredFields = descriptor.fields.filterNot { it.value.value == defaultValueForType(it.value.type.kexType) }
        val newObject = ObjectDescriptor(descriptor.klass)
        for ((name, field) in filteredFields) {
            newObject[name] = field.copy(owner = newObject)
        }
        return newObject
    }

    private fun defaultValueForType(type: KexType) = descriptor(context) {
        default(type)
    }
}