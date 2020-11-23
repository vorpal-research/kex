package org.jetbrains.research.kex.reanimator.descriptor

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.UnknownCall

object DescriptorStatistics {
    private val failures = mutableSetOf<Descriptor>()
    private var depth = 0L
    private var successes = 0
    private var successTime = 0L
    private var failTime = 0L
    private var nonTrivialDepth = 0L
    private var nonTrivialCount = 0L

    val CallStack.isComplete: Boolean get() {
        val visited = mutableSetOf<CallStack>()
        val queue = queueOf(this)

        while (queue.isNotEmpty()) {
            val top = queue.poll()
            if (top in visited) continue
            visited += top

            if (top.any { it is UnknownCall }) {
                return false
            }

            top.flatMap { it.parameters }.forEach {
                queue += it
            }
        }

        return true
    }

    fun addDescriptor(descriptor: Descriptor, callStack: CallStack, time: Long) {
        val descDepth = descriptor.depth
        depth += descDepth
        if (descDepth > 1) {
            nonTrivialCount++
            nonTrivialDepth += descDepth
        }
        when {
            callStack.isComplete -> {
                ++successes
                successTime += time
            }
            else -> {
                failures += descriptor
                failTime += time
            }
        }
    }

    fun addFailure(descriptor: Descriptor) {
        failures += descriptor
    }

    fun printStatistics() {
        val totalSize = successes + failures.size
        val successRate = successes.toDouble() / totalSize
//        log.info("Unknown descriptors: ${failures.joinToString("\nDescriptor:\n", prefix = "\n")}")
        log.info("Descriptor generation: ${String.format("%.2f", successRate * 100)}%")
        log.info("Average descriptor depth: ${String.format("%.2f", depth.toDouble() / totalSize)}")
        log.info("Average non-trivial descriptor depth: ${String.format("%.2f", nonTrivialDepth.toDouble() / nonTrivialCount)}")
        log.info("Average time per successful descriptor generation: ${String.format("%.02f", successTime.toDouble() / successes)}")
        log.info("Average time per failed descriptor generation: ${String.format("%.02f", failTime.toDouble() / failures.size)}")
        log.info("Average time per descriptor generation: ${String.format("%.02f", (successTime + failTime).toDouble() / totalSize)}")
    }
}