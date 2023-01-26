package org.vorpal.research.kex.asm.analysis.symbolic

import ch.scheitlin.alex.java.StackTrace
import kotlinx.coroutines.*
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@Suppress("MemberVisibilityCanBePrivate")
private operator fun ClassManager.get(frame: StackTraceElement): Method {
    val entryClass = this[frame.className.asmString]
    return entryClass.getMethods(frame.methodName).first { method ->
        method.body.flatten().any { inst ->
            inst.location.file == frame.fileName && inst.location.line == frame.lineNumber
        }
    }
}

class CrashReproductionChecker(
    ctx: ExecutionContext,
    @Suppress("MemberVisibilityCanBePrivate")
    val stackTrace: StackTrace
) : SymbolicTraverser(ctx, ctx.cm[stackTrace.stackTraceLines.last()]) {
    override val pathSelector: SymbolicPathSelector = DequePathSelector()
    override val callResolver: SymbolicCallResolver = StackTraceCallResolver(
        stackTrace, DefaultCallResolver(ctx)
    )
    override val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

    private val targetInstructions = ctx.cm[stackTrace.stackTraceLines.first()].body.flatten().filter {
        it.location.line == stackTrace.stackTraceLines.first().lineNumber
    }.toSet()

    companion object {
        @ExperimentalTime
        @DelicateCoroutinesApi
        fun run(context: ExecutionContext, stackTrace: StackTrace) {
            val timeLimit = kexConfig.getIntValue("symbolic", "timeLimit", 100)

            val coroutineContext = newFixedThreadPoolContextWithMDC(1, "symbolic-dispatcher")
            runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    async { CrashReproductionChecker(context, stackTrace).analyze() }.await()
                }
            }
        }
    }

    override fun report(inst: Instruction, parameters: Parameters<Descriptor>, testPostfix: String) {
        if (inst in targetInstructions) {
            super.report(inst, parameters, testPostfix)
        }
    }
}
