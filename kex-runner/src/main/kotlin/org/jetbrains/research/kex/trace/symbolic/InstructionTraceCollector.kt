package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kfg.ir.value.instruction.Instruction

interface InstructionTraceCollector {
    val symbolicState: SymbolicState
    val trace: List<Instruction>

    fun methodEnter(
        className: String,
        methodName: String,
        argTypes: Array<String>,
        retType: String,
        instance: Any?,
        args: Array<Any?>
    )

    fun arrayLoad(
        value: String,
        arrayRef: String,
        index: String,
        concreteValue: Any?,
        concreteRef: Any?,
        concreteIndex: Any?
    )

    fun arrayStore(
        arrayRef: String,
        index: String,
        value: String,
        concreteRef: Any?,
        concreteIndex: Any?,
        concreteValue: Any?
    )

    fun binary(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    )

    fun branch(
        condition: String,
        currentBlock: String
    )

    fun call(
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
    )

    fun cast(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    )

    fun catch(
        exception: String,
        concreteException: Any?
    )

    // can't receive concrete value of cmp result because there is not separate cmp instruction on bytecode level,
    // it is combined with branch instruction
    fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteLhv: Any?,
        concreteRhv: Any?
    )

    fun enterMonitor(
        operand: String,
        concreteOperand: Any?
    )

    fun exitMonitor(
        operand: String,
        concreteOperand: Any?
    )

    fun fieldLoad(
        value: String,
        owner: String?,
        klass: String,
        field: String,
        type: String,
        concreteValue: Any?,
        concreteOwner: Any?
    )

    fun fieldStore(
        owner: String?,
        klass: String,
        field: String,
        type: String,
        value: String,
        concreteValue: Any?,
        concreteOwner: Any?
    )

    fun instanceOf(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    )

    fun jump(
        currentBlock: String,
        targetBlock: String
    )

    fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    )

    fun new(
        value: String,
        concreteValue: Any?
    )

    fun phi(
        value: String,
        concreteValue: Any?
    )

    fun ret(
        returnValue: String?,
        currentBlock: String,
        concreteValue: Any?
    )

    fun switch(
        value: String,
        currentBlock: String,
        concreteValue: Any?
    )

    fun tableSwitch(
        value: String,
        currentBlock: String,
        concreteValue: Any?
    )

    fun throwing(
        currentBlock: String,
        exception: String,
        concreteException: Any?
    )

    fun unary(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    )
}