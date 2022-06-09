package org.vorpal.research.kex.reanimator

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.state.PredicateStateAnalysis
import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.asm.util.visibility
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequenceExecutor
import org.vorpal.research.kex.reanimator.actionsequence.generator.ActionSequenceGenerator
import org.vorpal.research.kex.reanimator.actionsequence.generator.GeneratorContext
import org.vorpal.research.kex.util.kex
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kthelper.logging.info
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import kotlin.system.measureTimeMillis

class RandomObjectReanimator(
    val ctx: ExecutionContext,
    val target: Package,
    val psa: PredicateStateAnalysis,
    val visibilityLevel: Visibility
) {
    val random: Randomizer get() = ctx.random
    val cm: ClassManager get() = ctx.cm
    val generatorContext = GeneratorContext(ctx, psa, visibilityLevel)

    private val ClassManager.randomClass
        get() = this.concreteClasses
            .filter { it.pkg.isChild(target) }
            .filterNot { it.isEnum }
            .random(ctx.random)

    private val Any.isValid: Boolean
        get() {
            val kfgClass = (this.javaClass.kex.getKfgType(cm.type) as ClassType).klass
            if (this is Throwable) return false
            if (!instantiationManager.isInstantiable(kfgClass) || visibilityLevel > kfgClass.visibility) return false
            if (with(generatorContext) { kfgClass.orderedCtors }.isEmpty()) return false
            return true
        }

    private fun Descriptor.isValid(visited: Set<Descriptor> = setOf()): Boolean = when (this) {
        in visited -> true
        is ConstantDescriptor -> true
        is ObjectDescriptor -> when {
            this.klass.kfgClass(cm.type) !is ConcreteClass -> false
            this.klass.kfgClass(cm.type).isInheritorOf(cm["java/util/Map"]) -> false
            this.klass.kfgClass(cm.type).isInheritorOf(cm["java/util/Set"]) -> false
            else -> {
                val set = visited + this
                this.fields.all { it.value.isValid(set) }
            }
        }
        is ClassDescriptor -> {
            val set = visited + this
            this.fields.all { it.value.isValid(set) }
        }
        is ArrayDescriptor -> {
            val set = visited + this
            this.elements.all { it.value.isValid(set) }
        }
    }

    private fun randomObject(): Any {
        var res: Any? = null
        while (res == null) {
            val kfgClass = cm.randomClass
            val klass = tryOrNull { ctx.loader.loadClass(kfgClass) } ?: continue
            res = random.nextOrNull(klass)
        }

        return res
    }

    data class Stats(
        val totalAttempts: Int,
        val validAttempts: Int,
        val validDescriptorAttempts: Int,
        val successes: Int,
        val validDescriptorSuccesses: Int,
        val depth: Int,
        val successDepths: Int,
        val totalValidDescriptorDepth: Int,
        val validDescriptorDepths: Int,
        val time: Long,
        val validTime: Long
    ) {
        override fun toString() = buildString {
            appendLine("Total attempts: $totalAttempts")
            appendLine("Valid attempts: $validAttempts")
            appendLine("Valid descriptor attempts: $validDescriptorAttempts")
            appendLine("Total attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / totalAttempts)}%")
            appendLine("Valid attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / validAttempts)}%")
            appendLine(
                "Valid descriptor attempts success rate: ${
                    String.format(
                        "%.02f",
                        100 * validDescriptorSuccesses.toDouble() / validDescriptorAttempts
                    )
                }%"
            )
            appendLine("Average random descriptor depth: ${String.format("%.02f", depth.toDouble() / validAttempts)}")
            appendLine("Average success descriptor depth: ${String.format("%.02f", successDepths.toDouble() / successes)}")
            appendLine(
                "Average valid descriptor depth: ${
                    String.format(
                        "%.02f",
                        totalValidDescriptorDepth.toDouble() / validDescriptorAttempts
                    )
                }"
            )
            appendLine(
                "Average success valid descriptor depth: ${
                    String.format(
                        "%.02f",
                        validDescriptorDepths.toDouble() / validDescriptorSuccesses
                    )
                }"
            )
            appendLine("Average time per descriptor generation: ${String.format("%.02f", time.toDouble() / validAttempts)}")
            appendLine(
                "Average time per valid descriptor generation: ${
                    String.format(
                        "%.02f",
                        validTime.toDouble() / validDescriptorAttempts
                    )
                }"
            )
        }
    }

    private fun run(objects: List<Any>): Stats {
        var totalAttempts = 0
        var validAttempts = 0
        var validDescriptorAttempts = 0
        var successes = 0
        var validDescriptorSuccesses = 0
        var depth = 0
        var successDepths = 0
        var totalValidDescriptorDepth = 0
        var validDescriptorDepths = 0
        var time = 0L
        var validTime = 0L
        for (any in objects) {
            log.debug("Attempt: $totalAttempts")
            log.debug("Valid attempt: $validAttempts")
            log.debug("Valid descriptor attempt: $validDescriptorAttempts")
            ++totalAttempts

            if (any.isValid) ++validAttempts
            else continue

            val descriptor = convertToDescriptor(any)
            val descriptorDepth = descriptor.depth
            depth += descriptorDepth

            log.debug("Depth: $descriptorDepth")
            val originalDescriptor = descriptor.deepCopy()

            if (originalDescriptor.isValid()) {
                ++validDescriptorAttempts
                ++validTime
                totalValidDescriptorDepth += descriptorDepth
            }

            var actionSequence: ActionSequence? = null
            time += measureTimeMillis {
                actionSequence = tryOrNull { ActionSequenceGenerator(generatorContext).generateDescriptor(descriptor) }
            }

            val generatedAny = actionSequence?.let { stack ->
                `try` {
                    ActionSequenceExecutor(ctx).execute(stack)
                }.getOrElse {
                    null
                }
            }

            val structuralEq = originalDescriptor eq convertToDescriptor(generatedAny)
            if (structuralEq) {
                successDepths += descriptorDepth
                ++successes
                if (originalDescriptor.isValid()) {
                    validDescriptorDepths += descriptorDepth
                    ++validDescriptorSuccesses
                }
            }

            log.debug("Equality: $structuralEq")
        }

        log.info("Total attempts: $totalAttempts")
        log.info("Valid attempts: $validAttempts")
        log.info("Valid descriptor attempts: $validDescriptorAttempts")
        log.info("Total attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / totalAttempts)}%")
        log.info("Valid attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / validAttempts)}%")
        log.info(
            "Valid descriptor attempts success rate: ${
                String.format(
                    "%.02f",
                    100 * validDescriptorSuccesses.toDouble() / validDescriptorAttempts
                )
            }%"
        )
        log.info("Average random descriptor depth: ${String.format("%.02f", depth.toDouble() / validAttempts)}")
        log.info("Average success descriptor depth: ${String.format("%.02f", successDepths.toDouble() / successes)}")
        log.info(
            "Average valid descriptor depth: ${
                String.format(
                    "%.02f",
                    totalValidDescriptorDepth.toDouble() / validDescriptorAttempts
                )
            }"
        )
        log.info(
            "Average success valid descriptor depth: ${
                String.format(
                    "%.02f",
                    validDescriptorDepths.toDouble() / validDescriptorSuccesses
                )
            }"
        )
        log.info("Average time per descriptor generation: ${String.format("%.02f", time.toDouble() / validAttempts)}")
        log.info(
            "Average time per valid descriptor generation: ${
                String.format(
                    "%.02f",
                    validTime.toDouble() / validDescriptorAttempts
                )
            }"
        )
        return Stats(
            totalAttempts, validAttempts, validDescriptorAttempts,
            successes, validDescriptorSuccesses, depth, successDepths,
            totalValidDescriptorDepth, validDescriptorDepths, time, validTime
        )
    }

    private fun generateObjects(attempts: Int): List<Any> {
        var totalAttempts = 0
        var validDescriptorAttempts = 0
        val objects = mutableListOf<Any>()
        while (validDescriptorAttempts < attempts) {
            ++totalAttempts

            val any = randomObject()
            if (!any.isValid) continue

            val descriptor = convertToDescriptor(any)

            if (descriptor.isValid()) {
                ++validDescriptorAttempts
            }

            objects += any
        }
        return objects
    }

    fun run(attempts: Int = 1000) {
        val objects = generateObjects(attempts)
        val stats = run(objects)
        log.info(stats)
    }

    fun runTestDepth(range: IntRange, attempts: Int = 1000) {
        val objects = generateObjects(attempts)
        val stats = range.map { depth ->
            RuntimeConfig.setValue("reanimator", "maxStackSize", depth)
            depth to run(objects)
        }
        for ((depth, stat) in stats) {
            log.info("-------")
            log.info("Running with depth $depth")
            log.info(stat)
            log.info("-------")
        }
    }
}
