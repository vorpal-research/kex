package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kex.trace.symbolic.TraceCollectorProxy
import org.jetbrains.research.kfg.ir.Method

class SymbolicTracingRunner(
    val ctx: ExecutionContext,
    method: Method
) : TracingAbstractRunner<SymbolicState>(method, ctx.loader) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): SymbolicState {
        val collector = TraceCollectorProxy.enableCollector(ctx)
        run(instance, args)
        TraceCollectorProxy.disableCollector()
        return collector.symbolicState
    }
}

class RandomSymbolicTracingRunner(
    val ctx: ExecutionContext,
    method: Method
) : TracingRandomRunner<SymbolicState>(method, ctx.loader, ctx.random) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): SymbolicState {
        val collector = TraceCollectorProxy.enableCollector(ctx)
        run(instance, args)
        TraceCollectorProxy.disableCollector()
        return collector.symbolicState
    }
}