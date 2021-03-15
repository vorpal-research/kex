package org.jetbrains.research.kex.trace.runner

import org.jetbrains.research.kthelper.logging.log
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.ParseException
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.trace.file.ActionParseException
import org.jetbrains.research.kex.trace.file.ActionParser
import org.jetbrains.research.kex.trace.file.Trace
import org.jetbrains.research.kfg.ir.Method

private fun parse(method: Method, result: InvocationResult): Trace {
    val traceFile = TraceInstrumenter.getTraceFile(method)
    val trace = traceFile.readText().split(";").map { it.trim() }

    val lines = trace.filter { it.isNotBlank() }

    val parser = ActionParser(method.cm)

    val actions = lines
            .mapNotNull {
                try {
                    parser.parseToEnd(it)
                } catch (e: ParseException) {
                    log.error("Failed to parse $method output: $e")
                    log.error("Failed line: $it")
                    null
                } catch (e: ActionParseException) {
                    log.error("Failed to parse $method output: $e")
                    log.error("Failed line: $it")
                    null
                }
            }

    return Trace.parse(actions, result.exception)
}

class FileTracingRunner(method: Method, loader: ClassLoader) : TracingAbstractRunner<Trace>(method, loader) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val result = run(instance, args)
        return parse(this.method, result)
    }
}

class RandomFileTracingRunner(method: Method, loader: ClassLoader, random: Randomizer)
    : TracingRandomRunner<Trace>(method, loader, random) {
    override fun collectTrace(instance: Any?, args: Array<Any?>): Trace {
        val result = run(instance, args)
        return parse(this.method, result)
    }
}