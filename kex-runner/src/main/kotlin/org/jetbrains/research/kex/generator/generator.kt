package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.ArrayType

class CallStackGenerator(val context: ExecutionContext, val psa: PredicateStateAnalysis) {

    // todo: accessibility check
    fun generate(descriptor: Descriptor): CallStack {
        var callStack = CallStack()
        when (descriptor) {
            is ConstantDescriptor -> callStack += when (descriptor) {
                is ConstantDescriptor.Null -> PrimaryValue(null)
                is ConstantDescriptor.Bool -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Int -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Long -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Float -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Double -> PrimaryValue(descriptor.value)
                is ConstantDescriptor.Class -> PrimaryValue(descriptor.value)
            }
            is ObjectDescriptor -> {
                val klass = descriptor.klass

                for (method in klass.methods.filter { it.isConstructor }) {
                    if (method.isEmpty()) continue
                    val builder = psa.builder(method)
                    val state = builder.getInstructionState(method.last().last())
                            ?: unreachable { log.error("Can't build state for constructor $method") }

                    val checker = Checker(method, context.loader, psa)
                    val result = checker.check(state, descriptor.toState())
                    if (result is Result.SatResult) {
                        val argumentDescriptors = generateDescriptors(method, context, result.model, checker.state).second
                        callStack += when {
                            method.argTypes.isEmpty() -> DefaultConstructorCall(klass)
                            else -> ConstructorCall(klass, method, argumentDescriptors.map { generate(it) })
                        }
                    }
                    return callStack
                }
                callStack += UnknownCall(descriptor)
            }
            is ArrayDescriptor -> {
                val elementType = (descriptor.type as ArrayType).component
                val array = NewArray(elementType, PrimaryValue(descriptor.length).wrap()).wrap()

                descriptor.elements.forEach { (index, value) ->
                    callStack += ArrayWrite(array, PrimaryValue(index).wrap(), generate(value))
                }
            }
            is FieldDescriptor -> {
                callStack += UnknownCall(descriptor)
            }
        }
        return callStack
    }
}