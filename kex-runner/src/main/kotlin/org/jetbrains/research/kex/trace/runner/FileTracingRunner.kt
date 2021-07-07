package org.jetbrains.research.kex.trace.runner

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.ParseException
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.trace.file.ActionParseException
import org.jetbrains.research.kex.trace.file.ActionParser
import org.jetbrains.research.kex.trace.file.FileTrace
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.NameMapperContext
import org.jetbrains.research.kthelper.logging.log

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