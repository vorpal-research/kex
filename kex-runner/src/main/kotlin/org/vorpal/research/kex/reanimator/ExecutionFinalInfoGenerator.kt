package org.vorpal.research.kex.reanimator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asserter.ExecutionExceptionFinalInfo
import org.vorpal.research.kex.asserter.ExecutionFinalInfo
import org.vorpal.research.kex.asserter.ExecutionSuccessFinalInfo
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.DescriptorBuilder
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.generator.ConcolicSequenceGenerator
import org.vorpal.research.kex.state.term.ArgumentTerm
import org.vorpal.research.kex.trace.symbolic.protocol.ExceptionResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionCompletedResult
import org.vorpal.research.kex.trace.symbolic.protocol.ExecutionResult
import org.vorpal.research.kex.trace.symbolic.protocol.SuccessResult
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Constant
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst

class ExecutionFinalInfoGenerator(val ctx: ExecutionContext, val method: Method) {
    private val asGenerator = ConcolicSequenceGenerator(ctx, PredicateStateAnalysis(ctx.cm))

    fun extractFinalInfo(executionResult: ExecutionResult): ExecutionFinalInfo<Descriptor>? = when (executionResult) {
        is ExecutionCompletedResult -> {
            val instance = executionResult.trace.concreteValues.entries
                    .firstOrNull { it.key.name == "this" }?.value?.deepCopy()
            val args = executionResult.trace.concreteValues
                    .filterKeys { it is ArgumentTerm }.map { it.value.deepCopy() }

            when(executionResult) {
                is ExceptionResult -> {
                    var exceptionDescriptor: ObjectDescriptor = executionResult.cause as ObjectDescriptor
                    val exceptionType = KexClass("java/lang/Throwable")
                    while (exceptionDescriptor.fields["target" to exceptionType] as? ObjectDescriptor != null) {
                        exceptionDescriptor = exceptionDescriptor.fields["target" to exceptionType] as ObjectDescriptor
                    }
                    val exceptionClassName = exceptionDescriptor.type.javaName

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
                        val retTerm = executionResult.trace.termMap.entries.firstOrNull { it.value.value == retValue }?.key
                        executionResult.trace.concreteValues[retTerm]
                    }
                    ExecutionSuccessFinalInfo(instance, args, retDescriptor)
                }
            }
        }
        else -> null
    }

    fun generateFinalInfoActionSequences(executionFinalInfo: ExecutionFinalInfo<Descriptor>?) : ExecutionFinalInfo<ActionSequence>? {
        if (executionFinalInfo == null) return null
        val instance = executionFinalInfo.instance?.let { asGenerator.generate(it) }
        val args = executionFinalInfo.args.map { asGenerator.generate(it) }
        return when(executionFinalInfo) {
            is ExecutionExceptionFinalInfo ->
                ExecutionExceptionFinalInfo(instance, args, executionFinalInfo.javaClass)
            is ExecutionSuccessFinalInfo ->
                ExecutionSuccessFinalInfo(instance, args, executionFinalInfo.retValue?.let { asGenerator.generate(it) })
        }
    }

}