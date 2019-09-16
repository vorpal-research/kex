package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kex.trace.`object`.Trace
import org.jetbrains.research.kex.trace.`object`.TraceCollectorProxy
import org.jetbrains.research.kfg.ir.Method

class ObjectTracingRunner(method: Method, loader: ClassLoader)
    : TracingAbstractRunner<Trace>(method, loader) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val collector = TraceCollectorProxy.initCollector(method.cm)
        invoke(instance, args)
        return Trace(collector.trace)
    }
}

class RandomObjectTracingRunner(method: Method, loader: ClassLoader) : TracingRandomRunner<Trace>(method, loader) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val collector = TraceCollectorProxy.initCollector(method.cm)
        invoke(instance, args)
        return Trace(collector.trace)
    }
}