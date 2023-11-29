package org.vorpal.research.kex.asserter

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.parameters.Parameters

sealed class ExecutionFinalInfo<T>(val instance: T?, val args: List<T>) {
    fun isException(): Boolean = this is ExecutionExceptionFinalInfo
}

class ExecutionSuccessFinalInfo<T>(instance: T?, args: List<T>, val retValue: T?) :
        ExecutionFinalInfo<T>(instance, args) {

    override fun equals(other: Any?): Boolean {
        if (other !is ExecutionSuccessFinalInfo<*>) return false
        return instance == other.instance && args.containsAll(other.args) && other.args.containsAll(args) && retValue == other.retValue
    }

    override fun hashCode(): Int = (instance?.hashCode() ?: 0) * 239 + args.hashCode() * 101 + (retValue?.hashCode() ?: 0)

}

// TODO: think about storing descriptors and action sequences
class ExecutionExceptionFinalInfo<T>(instance: T?, args: List<T>, val javaClass: String):
        ExecutionFinalInfo<T>(instance, args)

fun Parameters<Descriptor>.extractExceptionFinalInfo(exceptionJavaName: String): ExecutionExceptionFinalInfo<Descriptor> =
    ExecutionExceptionFinalInfo(instance, arguments, exceptionJavaName)

fun Parameters<Descriptor>.extractSuccessFinalInfo(returnValueDescriptor: Descriptor?) =
    ExecutionSuccessFinalInfo(instance, arguments, returnValueDescriptor)
