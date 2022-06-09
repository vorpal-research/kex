package org.vorpal.research.kex.trace.runner

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.trace.symbolic.InstructionTraceCollector
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.trace.symbolic.TraceCollectorProxy
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext

class SymbolicTracingRunner(
    val ctx: ExecutionContext,
    val nameContext: NameMapperContext,
    method: Method,
    val parameters: Parameters<Any?>
) : TracingAbstractRunner<SymbolicState>(method, ctx.loader) {
    private lateinit var collector: InstructionTraceCollector

    override fun generateArguments() = parameters

    override fun enableCollector() {
        collector = TraceCollectorProxy.enableCollector(ctx, nameContext)
    }

    override fun disableCollector() {
        TraceCollectorProxy.disableCollector()
    }

    override fun collectTrace(invocationResult: InvocationResult) = collector.symbolicState
}

class RandomSymbolicTracingRunner(
    val ctx: ExecutionContext,
    val nameContext: NameMapperContext,
    method: Method
) : TracingRandomRunner<SymbolicState>(method, ctx.loader, ctx.random) {
    private lateinit var collector: InstructionTraceCollector

    override fun enableCollector() {
        collector = TraceCollectorProxy.enableCollector(ctx, nameContext)
    }

    override fun disableCollector() {
        TraceCollectorProxy.disableCollector()
    }

    override fun collectTrace(invocationResult: InvocationResult) = collector.symbolicState
}