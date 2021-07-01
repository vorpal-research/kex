package org.jetbrains.research.kex.trace.symbolic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import java.nio.file.Path

private class EmptyTraceCollector : InstructionTraceCollector {
    class EmptyPathCondition: PathCondition() {
        override val path = emptyList<Clause>()

    }

    class EmptyState : SymbolicState() {
        override val state = emptyState()
        override val path = EmptyPathCondition()
        override val concreteValueMap = emptyMap<Term, Descriptor>()
        override val termMap = emptyMap<Term, WrappedValue>()
        override val predicateMap = emptyMap<Predicate, Instruction>()
        override val trace = InstructionTrace()
    }

    override val symbolicState = EmptyState()

    override fun methodEnter(
        className: String,
        methodName: String,
        argTypes: List<String>,
        retType: String,
        instance: Any?,
        args: List<Any?>
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
        inst: String,
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

    override fun branch(inst: String, condition: String) {}

    override fun call(
        inst: String,
        className: String,
        methodName: String,
        argTypes: List<String>,
        retType: String,
        returnValue: String?,
        callee: String?,
        arguments: List<String>,
        concreteArguments: List<Any?>
    ) {}

    override fun cast(value: String, operand: String, concreteValue: Any?, concreteOperand: Any?) {}

    override fun catch(exception: String, concreteException: Any?) {}

    override fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) {}

    override fun enterMonitor(inst: String, operand: String, concreteOperand: Any?) {}

    override fun exitMonitor(inst: String, operand: String, concreteOperand: Any?) {}

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
        inst: String,
        owner: String?,
        klass: String,
        field: String,
        type: String,
        value: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) {}

    override fun instanceOf(value: String, operand: String, concreteValue: Any?, concreteOperand: Any?) {}

    override fun jump(inst: String) {}

    override fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    ) {}

    override fun new(value: String) {}

    override fun phi(value: String, concreteValue: Any?) {}

    override fun ret(inst: String, returnValue: String?, concreteValue: Any?) {}

    override fun switch(inst: String, value: String, concreteValue: Any?) {}

    override fun tableSwitch(inst: String, value: String, concreteValue: Any?) {}

    override fun throwing(inst: String, exception: String, concreteException: Any?) {}

    override fun unary(value: String, operand: String, concreteValue: Any?, concreteOperand: Any?) {}
}

object TraceCollectorProxy {
    private lateinit var ctx: ExecutionContext
    private lateinit var collector: InstructionTraceCollector

    @JvmStatic
    fun enableCollector(ctx: ExecutionContext): InstructionTraceCollector {
        this.ctx = ctx
        collector = SymbolicTraceBuilder(ctx)
        return collector
    }

    @JvmStatic
    fun currentCollector() = collector

    @JvmStatic
    fun disableCollector() {
        collector = EmptyTraceCollector()
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @JvmStatic
    fun disableCollectorAndSaveTrace(output: Path) {
        val finalCollector = collector
        disableCollector()
        val serializer = KexSerializer(ctx.cm)
        val state = serializer.toJson(finalCollector.symbolicState)
        output.toFile().also {
            it.parentFile?.mkdirs()
        }.writeText(state)
    }

    @JvmStatic
    fun initializeEmptyCollector(): InstructionTraceCollector {
        collector = EmptyTraceCollector()
        return collector
    }
}