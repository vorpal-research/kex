package org.vorpal.research.kex.reanimator.descriptor

import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.reanimator.actionsequence.ActionList
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.PrimaryValue
import org.vorpal.research.kex.reanimator.actionsequence.StringValue
import org.vorpal.research.kex.reanimator.actionsequence.UnknownSequence
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import java.util.*

object DescriptorStatistics {
    private val failures = mutableSetOf<Descriptor>()
    private val exceptionFailures = mutableSetOf<Descriptor>()
    private var depth = 0L
    private var successes = 0L
    private var successTime = 0L
    private var failTime = 0L
    private var nonTrivialSuccesses = 0L
    private var nonTrivialDepth = 0L
    private var nonTrivialTime = 0L
    private var nonTrivialCount = 0L

    fun addDescriptor(descriptor: Descriptor, actionSequence: ActionSequence, time: Long) {
        val descDepth = descriptor.depth
        depth += descDepth
        if (descDepth > 1) {
            nonTrivialCount++
            nonTrivialDepth += descDepth
            nonTrivialTime += time
        }
        when (actionSequence) {
            is UnknownSequence -> {
                failures += descriptor
                failTime += time
            }

            is PrimaryValue<*> -> {
                ++successes
            }

            is StringValue -> {
                ++successes
            }

            is ActionList -> when {
                actionSequence.isComplete -> {
                    ++successes
                    if (descDepth > 1) ++nonTrivialSuccesses
                    successTime += time
                }

                else -> {
                    failures += descriptor
                    failTime += time
                }
            }

            else -> log.error { "Unexpected type of action sequence in executor: $actionSequence" }
        }
    }

    fun addFailure(descriptor: Descriptor) {
        log.warn("Descriptor generation failure: $descriptor")
        exceptionFailures += descriptor
    }

    private infix fun Double.safeDiv(other: Long) = when (other) {
        0L -> 0.0
        else -> this / other
    }

    @Suppress("unused")
    fun printStatistics() {
        val totalSize = successes + failures.size

        val successRate = (successes.toDouble() safeDiv totalSize) * 100
        val nonTrivialSuccessRate = (nonTrivialSuccesses.toDouble() safeDiv nonTrivialCount) * 100
        val avgDepth = depth.toDouble() safeDiv totalSize
        val avgNonTrivialDepth = nonTrivialDepth.toDouble() safeDiv nonTrivialCount
        val avgSuccessTime = successTime.toDouble() safeDiv successes
        val avgNonTrivialTime = nonTrivialTime.toDouble() safeDiv nonTrivialCount
        val avgFailedTime = failTime.toDouble() safeDiv failures.size.toLong()
        val avgTotalTime = (successTime + failTime).toDouble() safeDiv totalSize

        if (exceptionFailures.isNotEmpty()) {
            log.warn("There are ${exceptionFailures.size} exception failures when generating descriptors")
        }

        log.info("Descriptor generation: ${String.format(Locale.ENGLISH, "%.2f", successRate)}%")
        log.info("Non-trivial descriptor generation: ${String.format(Locale.ENGLISH, "%.2f", nonTrivialSuccessRate)}%")
        log.info("Average descriptor depth: ${String.format(Locale.ENGLISH, "%.2f", avgDepth)}")
        log.info("Average non-trivial descriptor depth: ${String.format(Locale.ENGLISH, "%.2f", avgNonTrivialDepth)}")
        log.info(
            "Average time per successful descriptor generation: ${
                String.format(
                    Locale.ENGLISH,
                    "%.02f",
                    avgSuccessTime
                )
            }"
        )
        log.info(
            "Average time per failed descriptor generation: ${
                String.format(
                    Locale.ENGLISH,
                    "%.02f",
                    avgFailedTime
                )
            }"
        )
        log.info("Average time per descriptor generation: ${String.format(Locale.ENGLISH, "%.02f", avgTotalTime)}")
        log.info(
            "Average time per non-trivial descriptor generation: ${
                String.format(
                    Locale.ENGLISH,
                    "%.02f",
                    avgNonTrivialTime
                )
            }"
        )
    }
}
