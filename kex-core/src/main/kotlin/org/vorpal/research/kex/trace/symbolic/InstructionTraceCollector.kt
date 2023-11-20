package org.vorpal.research.kex.trace.symbolic

import org.vorpal.research.kfg.ir.value.instruction.Instruction

interface InstructionTraceCollector {
    val instructionTrace: List<Instruction>
    val symbolicState: SymbolicState

    fun track(value: String, concreteValue: Any?)

    fun methodEnter(
        className: String,
        methodName: String,
        argTypes: List<String>,
        retType: String,
        instance: Any?,
        args: List<Any?>
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
        inst: String,
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
        inst: String,
        condition: String
    )

    fun call(
        inst: String,
        className: String,
        methodName: String,
        argTypes: List<String>,
        retType: String,
        returnValue: String?,
        callee: String?,
        arguments: List<String>,
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

    // can't receive concrete value of cmp result because there is no separate cmp instruction on bytecode level,
    // it is combined with branch instruction
    fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteLhv: Any?,
        concreteRhv: Any?
    )

    fun enterMonitor(
        inst: String,
        operand: String,
        concreteOperand: Any?
    )

    fun exitMonitor(
        inst: String,
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
        inst: String,
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

    fun invokeDynamic(
        value: String,
        operands: List<String>,
        concreteValue: Any?,
        concreteOperands: List<Any?>
    )

    fun jump(
        inst: String
    )

    fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    )

    fun new(
        value: String
    )

    fun phi(
        value: String,
        concreteValue: Any?
    )

    fun ret(
        inst: String,
        returnValue: String?,
        concreteValue: Any?
    )

    fun switch(
        inst: String,
        value: String,
        concreteValue: Any?
    )

    fun tableSwitch(
        inst: String,
        value: String,
        concreteValue: Any?
    )

    fun throwing(
        inst: String,
        exception: String,
        concreteException: Any?
    )

    fun unary(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    )

    fun addNullityConstraints(
        inst: String,
        value: String,
        concreteValue: Any?
    )

    fun addTypeConstraints(
        inst: String,
        value: String,
        concreteValue: Any?
    )

    fun addTypeConstraints(
        inst: String,
        value: String,
        type: String,
        concreteValue: Any?
    )

    fun addArrayIndexConstraints(
        inst: String,
        array: String,
        index: String,
        concreteArray: Any?,
        concreteIndex: Any?
    )

    fun addArrayLengthConstraints(
        inst: String,
        length: String,
        concreteLength: Any?
    )
}
