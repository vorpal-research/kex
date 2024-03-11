package org.vorpal.research.kex.asm.analysis.symbolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.newFixedThreadPoolContextWithMDC
import org.vorpal.research.kfg.ir.Method
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


class InstructionSymbolicChecker(
    ctx: ExecutionContext,
    rootMethod: Method,
) : SymbolicTraverser(ctx, rootMethod) {
    override val pathSelector: SymbolicPathSelector
    override val callResolver: SymbolicCallResolver = DefaultCallResolver(ctx)
    override val invokeDynamicResolver: SymbolicInvokeDynamicResolver = DefaultCallResolver(ctx)

    init {
        val pathSelectorName: String = kexConfig.getStringValue("kex", "pathSelector", "NSubpathPathSelector")
        val n: Int = kexConfig.getIntValue("kex", "nPathSelectorArg", 2)
        pathSelector = when (pathSelectorName) {
            "DequePathSelector" -> DequePathSelector()
            "NSubpathPathSelector" -> NSubpathPathSelector(n)
            "PriorityDequePathSelector" -> PriorityDequePathSelector(n)
            else -> throw IllegalArgumentException("PathSelector '$pathSelectorName' doesn't exist. " +
                    "Check InstructionSymbolicChecker to see available path selectors")
        }
    }

    companion object {
        @ExperimentalTime
        @DelicateCoroutinesApi
        fun run(context: ExecutionContext, targets: Set<Method>) {
            val executors = kexConfig.getIntValue("symbolic", "numberOfExecutors", 8)
            val timeLimit = kexConfig.getIntValue("symbolic", "timeLimit", 100)

            val actualNumberOfExecutors = maxOf(1, minOf(executors, targets.size))
            val coroutineContext = newFixedThreadPoolContextWithMDC(actualNumberOfExecutors, "symbolic-dispatcher")
            runBlocking(coroutineContext) {
                withTimeoutOrNull(timeLimit.seconds) {
                    targets.map {
                        async { InstructionSymbolicChecker(context, it).analyze() }
                    }.awaitAll()
                }
            }
        }
    }
}
