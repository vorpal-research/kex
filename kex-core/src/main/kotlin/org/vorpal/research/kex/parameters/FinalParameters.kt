package org.vorpal.research.kex.parameters

import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.trace.symbolic.protocol.ExceptionResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.SuccessResult
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kfg.type.SystemTypeNames

sealed class FinalParameters<T> {
    abstract val instance: T?
    abstract val args: List<T>

    val hasRetValue: Boolean
        get() = this is SuccessFinalParameters && retValue != null
    val isException: Boolean
        get() = this is ExceptionFinalParameters

    abstract fun flatten(): Collection<T>
}

class SuccessFinalParameters<T>(
    override val instance: T?,
    override val args: List<T>,
    val retValue: T?
) : FinalParameters<T>() {
    override fun flatten(): Collection<T> {
        val result = mutableListOf<T>()
        instance?.let { result += it }
        result += args
        retValue?.let { result += it }
        return result
    }
}

class ExceptionFinalParameters<T>(
    override val instance: T?,
    override val args: List<T>,
    val javaClass: String
) : FinalParameters<T>() {
    override fun flatten(): Collection<T> {
        val result = mutableListOf<T>()
        instance?.let { result += it }
        result += args
        return result
    }
}

fun Parameters<Descriptor>.extractExceptionFinalParameters(exceptionJavaName: String): ExceptionFinalParameters<Descriptor> =
    ExceptionFinalParameters(instance, arguments, exceptionJavaName)

fun Parameters<Descriptor>.extractSuccessFinalParameters(returnValueDescriptor: Descriptor?) =
    SuccessFinalParameters(instance, arguments, returnValueDescriptor)

fun extractFinalParameters(executionResult: ExecutionResult, method: Method): FinalParameters<Descriptor>? =
    when (executionResult) {
        is ExecutionCompletedResult -> {
            val instance = executionResult.symbolicState.concreteValues.entries
                .firstOrNull { it.key.name == "this" }?.value?.deepCopy()
            val args = executionResult.symbolicState.concreteValues
                .filterKeys { it is ArgumentTerm }.map { it.value.deepCopy() }

            when (executionResult) {
                is ExceptionResult -> {
                    var exceptionDescriptor = executionResult.cause as ObjectDescriptor
                    val exceptionType = KexClass(SystemTypeNames.throwableClass)
                    while (exceptionDescriptor.fields["target" to exceptionType] as? ObjectDescriptor != null) {
                        exceptionDescriptor = exceptionDescriptor.fields["target" to exceptionType] as ObjectDescriptor
                    }
                    val exceptionClassName = exceptionDescriptor.type.javaName
                    if ("org.vorpal.research.kex" in exceptionClassName) {
                        throw IllegalArgumentException("Exception $exceptionDescriptor is from kex package")
                    }
                    ExceptionFinalParameters(instance, args, exceptionClassName)
                }

                is SuccessResult -> {
                    val retInst = method.body.bodyBlocks
                        .flatten()
                        .filterIsInstance<ReturnInst>()
                        .firstOrNull()
                    val retValue = when (retInst?.hasReturnValue) {
                        true -> retInst.returnValue
                        else -> null
                    }
                    val retDescriptor = when (retValue) {
                        is Constant -> descriptor { const(retValue) }

                        else -> {
                            val retTerm = executionResult.symbolicState.termMap.entries.firstOrNull {
                                it.value.value == retValue && it.value.depth == 0
                            }?.key
                            val descriptor = executionResult.symbolicState.concreteValues[retTerm]
                            when (method.returnType) {
                                is BoolType -> (descriptor as? ConstantDescriptor.Int)?.toBool()
                                else -> descriptor
                            }
                        }
                    }

                    SuccessFinalParameters(instance, args, retDescriptor)
                }
            }
        }

        else -> null
    }
