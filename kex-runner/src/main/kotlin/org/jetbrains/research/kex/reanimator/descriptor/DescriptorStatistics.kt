package org.jetbrains.research.kex.reanimator.descriptor

import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.UnknownCall

object DescriptorStatistics {
    private val failures = mutableSetOf<Descriptor>()
    private var successes = 0

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
        }

        return true
    }

    fun addDescriptor(descriptor: Descriptor, callStack: CallStack) {
        when {
            callStack.isComplete -> ++successes
            else -> failures += descriptor
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
    }
}