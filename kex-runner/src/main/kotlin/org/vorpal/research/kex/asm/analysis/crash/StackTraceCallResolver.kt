package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.asm.analysis.symbolic.SymbolicCallResolver
import org.vorpal.research.kex.asm.analysis.symbolic.TraverserState
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.CallInst

class StackTraceCallResolver(
    stackTrace: StackTrace,
    val fallback: SymbolicCallResolver
) : SymbolicCallResolver {
    private val stackTraceLines = stackTrace.stackTraceLines.reversed()

    private infix fun Pair<Method, Location>.eq(stackTraceElement: StackTraceElement): Boolean {
        val (method, location) = this
        return method.klass.fullName.javaString == stackTraceElement.className
                && method.name == stackTraceElement.methodName
                && location.file == stackTraceElement.fileName
                && location.line == stackTraceElement.lineNumber
    }

    override fun resolve(state: TraverserState, inst: CallInst): List<Method> {
        val currentTrace = state.stackTrace.map { it.method to it.instruction.location } +
                (inst.parent.method to inst.location)
        return when {
            currentTrace.size < stackTraceLines.lastIndex
                    && currentTrace.withIndex().all { (index, it) -> it eq stackTraceLines[index] }
                    && (inst.method.name == stackTraceLines[currentTrace.size].methodName
                    && inst.method.klass.fullName.javaString == stackTraceLines[currentTrace.size].className)-> {
                val res = listOf(inst.method.cm[stackTraceLines[currentTrace.size]].rtMapped)
                res
            }

            else -> {
                fallback.resolve(state, inst)
            }
        }.filter { it.body.isNotEmpty() }
    }
}
