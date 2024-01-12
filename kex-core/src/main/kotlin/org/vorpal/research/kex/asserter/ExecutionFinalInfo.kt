package org.vorpal.research.kex.asserter

import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.DescriptorBuilder
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.trace.symbolic.protocol.ExceptionResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.SuccessResult
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.toBoolean

sealed class ExecutionFinalInfo<T>() {
    abstract val instance: T?
    abstract val args: List<T>
    fun isException(): Boolean = this is ExecutionExceptionFinalInfo
}

class ExecutionSuccessFinalInfo<T>(
    override val instance: T?,
    override val args: List<T>,
    val retValue: T?
) : ExecutionFinalInfo<T>() {

    override fun equals(other: Any?): Boolean {
        if (other !is ExecutionSuccessFinalInfo<*>) return false
        return instance == other.instance && args == other.args && retValue == other.retValue
    }

    override fun hashCode(): Int = (instance?.hashCode() ?: 0) * 239 + args.hashCode() * 101 + (retValue?.hashCode() ?: 0)

}

class ExecutionExceptionFinalInfo<T>(
    override val instance: T?,
    override val args: List<T>,
    val javaClass: String
): ExecutionFinalInfo<T>()

fun Parameters<Descriptor>.extractExceptionFinalInfo(exceptionJavaName: String): ExecutionExceptionFinalInfo<Descriptor> =
    ExecutionExceptionFinalInfo(instance, arguments, exceptionJavaName)

fun Parameters<Descriptor>.extractSuccessFinalInfo(returnValueDescriptor: Descriptor?) =
    ExecutionSuccessFinalInfo(instance, arguments, returnValueDescriptor)

fun extractFinalInfo(executionResult: ExecutionResult, method: Method): ExecutionFinalInfo<Descriptor>? = when (executionResult) {
    is ExecutionCompletedResult -> {
        val instance = executionResult.symbolicState.concreteValues.entries
            .firstOrNull { it.key.name == "this" }?.value?.deepCopy()
        val args = executionResult.symbolicState.concreteValues
            .filterKeys { it is ArgumentTerm }.map { it.value.deepCopy() }
        log.debug("Printing extracted info:")
        log.debug("Instance:")
        log.debug(instance.toString())
        log.debug("Argumnts:")
        args.forEach { log.debug(it.toString()) }
        when(executionResult) {
            is ExceptionResult -> {
                var exceptionDescriptor: ObjectDescriptor = executionResult.cause as ObjectDescriptor
                val exceptionType = KexClass("java/lang/Throwable")
                while (exceptionDescriptor.fields["target" to exceptionType] as? ObjectDescriptor != null) {
                    exceptionDescriptor = exceptionDescriptor.fields["target" to exceptionType] as ObjectDescriptor
                }
                val exceptionClassName = exceptionDescriptor.type.javaName
                if (exceptionClassName.contains("org.vorpal.research.kex")) {
                    throw IllegalArgumentException("Exception $exceptionDescriptor is from kex package")
                }
                log.debug("Exception:")
                log.debug(exceptionClassName)
                ExecutionExceptionFinalInfo(instance, args, exceptionClassName)
            }
            is SuccessResult -> {
                val retInst = method.body.bodyBlocks
                    .map { it.instructions }.flatten()
                    .filterIsInstance<ReturnInst>().firstOrNull()
                val retValue = if (retInst?.hasReturnValue == true) retInst.returnValue else null
                val retDescriptor = if (retValue is Constant) {
                    DescriptorBuilder().const(retValue)
                } else {
                    val retTerm = executionResult.symbolicState.termMap.entries.firstOrNull {
                        it.value.value == retValue && it.value.depth == 0
                    }?.key
                    val descriptor = executionResult.symbolicState.concreteValues[retTerm]
                    if (method.returnType == BoolType) {
                        (descriptor as? ConstantDescriptor.Int)?.toBool()
                    } else {
                        descriptor
                    }
                }

                log.debug("Return value:")
                log.debug(retDescriptor.toString())
                ExecutionSuccessFinalInfo(instance, args, retDescriptor)
            }
        }
    }
    else -> null
}
