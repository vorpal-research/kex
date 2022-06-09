package org.vorpal.research.kex.trace.runner

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.ParseException
import org.vorpal.research.kex.asm.transform.TraceInstrumenter
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kex.trace.file.ActionParseException
import org.vorpal.research.kex.trace.file.ActionParser
import org.vorpal.research.kex.trace.file.FileTrace
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kthelper.logging.log

private fun parse(nameContext: NameMapperContext, method: Method, result: InvocationResult): FileTrace {
    val traceFile = TraceInstrumenter.getTraceFile(method)
    val trace = traceFile.readText().split(";").map { it.trim() }

    val lines = trace.filter { it.isNotBlank() }

    val parser = ActionParser(method.cm, nameContext)

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

    return FileTrace.parse(actions, result.exception)
}

class FileTracingRunner(
    val nameContext: NameMapperContext,
    method: Method,
    loader: ClassLoader,
    val parameters: Parameters<Any?>,
) : TracingAbstractRunner<FileTrace>(method, loader) {
    override fun generateArguments() = parameters

    override fun enableCollector() {}
    override fun disableCollector() {}

    override fun collectTrace(invocationResult: InvocationResult) = parse(nameContext, method, invocationResult)
}

class RandomFileTracingRunner(
    val nameContext: NameMapperContext,
    method: Method,
    loader: ClassLoader,
    random: Randomizer
) : TracingRandomRunner<FileTrace>(method, loader, random) {
    override fun enableCollector() {}
    override fun disableCollector() {}
    override fun collectTrace(invocationResult: InvocationResult) = parse(nameContext, method, invocationResult)
}