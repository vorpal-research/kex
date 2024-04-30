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
import org.vorpal.research.kthelper.assert.asserted

class FinalParameters<T> private constructor(
    val instance: T?,
    val args: List<T>,
    val returnValueUnsafe: T?,
    val exceptionTypeUnsafe: String?
) {
    val isException: Boolean
        get() = exceptionTypeUnsafe != null
    val isSuccess: Boolean
        get() = !isException
    val hasReturnValue: Boolean
        get() = returnValueUnsafe != null

    val exceptionType: String
        get() = asserted(isException) { exceptionTypeUnsafe!! }
    val returnValue: T
        get() = asserted(hasReturnValue) { returnValueUnsafe!! }
    val asList: List<T>
        get() = listOfNotNull(instance) + args + listOfNotNull(returnValueUnsafe)

    constructor(instance: T?, args: List<T>, exceptionType: String) :
            this(instance, args, null, exceptionType)

    constructor(instance: T?, args: List<T>, returnValue: T?) :
            this(instance, args, returnValue, null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FinalParameters<*>

        if (instance != other.instance) return false
        if (args != other.args) return false
        if (returnValueUnsafe != other.returnValueUnsafe) return false
        if (exceptionTypeUnsafe != other.exceptionTypeUnsafe) return false

        return true
    }

    override fun hashCode(): Int {
        var result = instance?.hashCode() ?: 0
        result = 31 * result + args.hashCode()
        result = 31 * result + (returnValueUnsafe?.hashCode() ?: 0)
        result = 31 * result + (exceptionTypeUnsafe?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = buildString {
        append("FinalParameters(instance=")
        append(instance)
        append(", args=")
        append(args)
        append(", returnValueUnsafe=")
        append(returnValueUnsafe)
        append(", exceptionTypeUnsafe=")
        append(exceptionTypeUnsafe)
        append(")")
    }
}

val <T> FinalParameters<T>?.isSuccessOrFalse: Boolean get() = this?.isSuccess ?: false
val <T> FinalParameters<T>?.isExceptionOrFalse: Boolean get() = this?.isException ?: false
val <T> FinalParameters<T>?.hasReturnValueOrFalse: Boolean get() = this?.hasReturnValue ?: false

fun Parameters<Descriptor>.extractFinalParameters(exceptionJavaName: String): FinalParameters<Descriptor> =
    FinalParameters(instance, arguments, exceptionJavaName)

fun Parameters<Descriptor>.extractFinalParameters(returnValueDescriptor: Descriptor?) =
    FinalParameters(instance, arguments, returnValueDescriptor)

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
                    FinalParameters(instance, args, exceptionClassName)
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

                    FinalParameters(instance, args, retDescriptor)
                }
            }
        }

        else -> null
    }
