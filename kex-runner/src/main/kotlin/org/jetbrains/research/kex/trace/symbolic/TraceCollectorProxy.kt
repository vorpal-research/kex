package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

private class EmptyTraceCollector : InstructionTraceCollector {
    override val trace = emptyList<Instruction>()
    override fun methodEnter(
        className: String,
        methodName: String,
        argTypes: Array<String>,
        retType: String,
        instance: Any?,
        args: Array<Any?>
    ) {}

    override fun arrayLoad(
        value: String,
        arrayRef: String,
        index: String,
        concreteValue: Any?,
        concreteRef: Any?,
        concreteIndex: Any?
    ) {}

    override fun arrayStore(
        arrayRef: String,
        index: String,
        value: String,
        concreteRef: Any?,
        concreteIndex: Any?,
        concreteValue: Any?
    ) {}

    override fun binary(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) {}

    override fun branch(condition: String, currentBlock: String, concreteCondition: Any?) {}

    override fun call(
        className: String,
        methodName: String,
        argTypes: Array<String>,
        retType: String,
        returnValue: String?,
        callee: String?,
        arguments: List<String>,
        concreteReturn: Any?,
        concreteCallee: Any?,
        concreteArguments: List<Any?>
    ) {}

    override fun cast(value: String, operand: String, concreteValue: Any?, concreteOperand: Any?) {}

    override fun catch(exception: String, concreteException: Any?) {}

    override fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) {}

    override fun enterMonitor(operand: String, concreteOperand: Any?) {}

    override fun exitMonitor(operand: String, concreteOperand: Any?) {}

    override fun fieldLoad(
        value: String,
        owner: String?,
        klass: String,
        field: String,
        type: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) {}

    override fun fieldStore(
        owner: String?,
        klass: String,
        field: String,
        type: String,
        value: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) {}

    override fun instanceOf(value: String, operand: String, concreteValue: Any?, concreteOperand: Any?) {}

    override fun jump(currentBlock: String, targetBlock: String) {}

    override fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    ) {}

    override fun new(value: String, concreteValue: Any?) {}

    override fun phi(value: String, concreteValue: Any?) {}

    override fun ret(returnValue: String?, currentBlock: String, concreteValue: Any?) {}

    override fun switch(value: String, currentBlock: String, concreteValue: Any?) {}

    override fun tableSwitch(value: String, currentBlock: String, concreteValue: Any?) {}

    override fun throwing(currentBlock: String, exception: String, concreteException: Any?) {}

    override fun unary(value: String, operand: String, concreteValue: Any?, concreteOperand: Any?) {}
}

object TraceCollectorProxy {
    private lateinit var collector: InstructionTraceCollector

    @JvmStatic
    fun enableCollector(ctx: ExecutionContext): InstructionTraceCollector {
        collector = SymbolicTraceBuilder(ctx)
        return collector
    }

    @JvmStatic
    fun currentCollector() = collector

    @JvmStatic
    fun disableCollector() {
        collector = EmptyTraceCollector()
    }

    @JvmStatic
    fun initializeEmptyCollector(): InstructionTraceCollector {
        collector = EmptyTraceCollector()
        return collector
    }
}