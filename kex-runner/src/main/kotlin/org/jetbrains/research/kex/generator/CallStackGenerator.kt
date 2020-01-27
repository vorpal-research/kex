package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.transformer.generateDescriptors
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import java.util.*

// todo: think about generating list of calls instead of call stack tree
class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {
    private val descriptorMap = mutableMapOf<Descriptor, Node>()

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
        queue += descriptor to CallStack()
        while (queue.isNotEmpty()) {
            val (desc, stack) = queue.pollFirst()

            generateConstructorCall(desc)?.run {
                return stack + this
            }

            for (method in klass.methods) {
                val (newDesc, args) = executeMethod(desc, method)
                if (newDesc != null && newDesc != desc) {
                    queue += newDesc to (stack + MethodCall(stack, method, args.map { generate(it) }))
                }
            }
        }
        return null
    }

    private fun generateConstructorCall(descriptor: ObjectDescriptor): ApiCall? {
        val klass = descriptor.klass
        for (method in klass.constructors) {
            val (thisDesc, args) = executeMethod(descriptor, method)
            if (thisDesc != null) {
                return when {
                    method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                    else -> ConstructorCall(klass, method, args.map { generate(it) })
                }
            }
        }
        return null
    }

    private fun executeMethod(descriptor: ObjectDescriptor, method: Method): Pair<ObjectDescriptor?, List<Descriptor>> {
        if (method.isEmpty()) return descriptor to listOf()
        val builder = psa.builder(method)
        val state = builder.methodState
                ?: unreachable { log.error("Can't build state for $method") }

        val checker = Checker(method, context.loader, psa)
        return when (val result = checker.check(state, descriptor.toState())) {
            is Result.SatResult -> {
                val (thisDescriptor, argumentDescriptors) = generateDescriptors(method, context, result.model, checker.state)
                (thisDescriptor as? ObjectDescriptor) to argumentDescriptors
            }
            else -> {
                log.warn("Could not use $method for generating $descriptor")
                null to listOf()
            }
        }
    }
}